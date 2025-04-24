#include <iostream>
#include <MNN/expr/Module.hpp>
#include <MNN/expr/Executor.hpp>
#include <MNN/expr/ExprCreator.hpp>
#include <MNN/expr/Executor.hpp>
#include <MNN/expr/ExecutorScope.hpp>
#include <MNN/expr/ExprCreator.hpp>
#include <MNN/expr/Expr.hpp>
#include <MNN/expr/MathOp.hpp>
#include <MNN/expr/NeuralNetWorkOp.hpp>
#include <fstream>
#include <sstream>
#include <MNN/Tensor.hpp>
#include <utility>
#include "bert_vits2_v23_loader.hpp"

namespace MNN_BERT_VITS2 {

    static bool g_initialized = false;

    MNN::Express::Module::Config mdconfig; // default module config
    static std::shared_ptr<MNN::Express::Module> g_enc_module = nullptr;
    static std::shared_ptr<MNN::Express::Module> g_dec_module = nullptr;
    static std::shared_ptr<MNN::Express::Module> g_sdp_module = nullptr;
    static std::shared_ptr<MNN::Express::Module> g_dp_module = nullptr;
    static std::shared_ptr<MNN::Express::Module> g_emb_module = nullptr;
    static std::shared_ptr<MNN::Express::Module> g_flow_module = nullptr;
    static std::shared_ptr<MNN::Express::Module> g_bert_module = nullptr;
    const static float seq_noise_scale = 0.6f;
    const static float sdp_noise_scale = 0.9f;
    const static float sdp_ratiof = 0.5f;
    static float g_length_scale = 1.f;

    //哼..哼..哼.. 啊啊啊...........
    const static int random_seed_0 = 114514;
    const static int random_seed_1 = 1919810;

    template <typename T>
    const T& clamp(const T& value, const T& minValue, const T& maxValue) {
        if (value < minValue) return minValue;
        if (value > maxValue) return maxValue;
        return value;
    }

    void print_output_dim(const std::string& output_name, const MNN::Express::VARP& output) {
        std::ostringstream dimInfo;
        auto output_info = output->getInfo();
        for (int d : output_info->dim) {
            dimInfo << d << ", ";
        }
        MNNBV2PRINT("Output Name: %s Dim: %s\n", output_name.c_str(), dimInfo.str().c_str());
    }
    void print_output_value(int limit, const std::string& name,  const MNN::Express::VARP& output, bool needInt = false){
        std::ostringstream valueInfo;
        if (needInt) {
            auto ptr = output->readMap<int>();
            for (int d=0; d<limit; ++d) {
                valueInfo << ptr[d] << ", ";
            }
        } else {
            auto ptr = output->readMap<float>();
            for (int d=0; d<limit; ++d) {
                valueInfo << ptr[d] << ", ";
            }
        }
        MNNBV2PRINT("Output Name: %s, Output Value: %s\n",name.c_str(), valueInfo.str().c_str());
    }

    std::vector<MNN::Express::VARP> translate_run_bert(const std::vector<int>& _input_ids,
                                                       const std::vector<int>& _input_word2ph,
                                                       const std::vector<int>& _attention_mask){
        MNNBV2PRINT("Initializing bert...");
        MNN::Express::VARP bert_input_ids = MNN::Express::_Input({1, (int)_input_ids.size()}, MNN::Express::NCHW, halide_type_of<int>());
        MNN::Express::VARP bert_attention_mask = MNN::Express::_Input({1, (int)_attention_mask.size()}, MNN::Express::NCHW, halide_type_of<int>());
        ::memcpy(bert_input_ids->writeMap<int>(), _input_ids.data(), _input_ids.size() * sizeof(int));
        ::memcpy(bert_attention_mask->writeMap<int>(), _attention_mask.data(), _attention_mask.size() * sizeof(int));
        MNNBV2PRINT("Load bert start forward\n");
        std::vector<MNN::Express::VARP> bert_outputs  = g_bert_module->onForward({bert_input_ids, bert_attention_mask});
        MNNBV2PRINT("bert forward finish\n");

        // repeat and concat
        // encoder onnx 导出的时候会在 forward 里做 transpose, 因此输入是 [x, 1024]，和原始的 pytorch 推理代码不太一样
        const auto &bert_res = MNN::Express::_Squeeze(bert_outputs[0], {0});
        std::vector<int> size_split(_input_word2ph.size(), 1);
        auto bert_res_list = MNN::Express::_Split(bert_res, size_split, 0);
        std::vector<MNN::Express::VARP> repeated_list;
        for (int index = 0; index < _input_word2ph.size(); index++) {
            auto shape = {_input_word2ph[index], 1};
            auto tileShape = _Const(data(shape), {2}, MNN::Express::NCHW, halide_type_of<int>());
            auto repeated = _Tile(bert_res_list[index], tileShape);  // 输出 shape [itemCount, 1024]
            repeated_list.push_back(repeated);
        }
        auto repeated_bert = MNN::Express::_Concat(repeated_list, 0); // 输出 shape [totalItemCount, 1024]
        auto output_bert_1 = MNN::Express::_RandomUnifom(MNN::Express::_Shape(repeated_bert, true), halide_type_of<float>(), 0.0f, 1.0f, random_seed_0, random_seed_1);
        auto output_bert_2 = MNN::Express::_RandomUnifom(MNN::Express::_Shape(repeated_bert, true), halide_type_of<float>(), 0.0f, 1.0f, random_seed_0, random_seed_1);

        return {repeated_bert, output_bert_1, output_bert_2};
    }

    std::vector<MNN::Express::VARP> translate_run_encoder(const std::vector<int>& _input_x,
                                                          const std::vector<int>& _input_t,
                                                          const std::vector<int>& _input_language,
                                                          const MNN::Express::VARP& bert_0,
                                                          const MNN::Express::VARP& bert_1,
                                                          const MNN::Express::VARP& bert_2,
                                                          const MNN::Express::VARP& g){
        MNNBV2PRINT("Initializing encoder...");
        MNN::Express::VARP x = MNN::Express::_Input({1, (int)_input_x.size()}, MNN::Express::NCHW, halide_type_of<int>());
        MNN::Express::VARP t = MNN::Express::_Input({1, (int)_input_t.size()}, MNN::Express::NCHW, halide_type_of<int>());
        MNN::Express::VARP language = MNN::Express::_Input({1, (int)_input_language.size()}, MNN::Express::NCHW, halide_type_of<int>());
        ::memcpy(x->writeMap<int>(), _input_x.data(), _input_x.size() * sizeof(int));
        ::memcpy(t->writeMap<int>(), _input_t.data(), _input_t.size() * sizeof(int));
        ::memcpy(language->writeMap<int>(), _input_language.data(), _input_language.size() * sizeof(int));
        MNNBV2PRINT("Load enc_p start forward\n");
        std::vector<MNN::Express::VARP> enc_outputs  = g_enc_module->onForward({x, t, language, bert_0, bert_1, bert_2, g});
        MNNBV2PRINT("enc_p forward finish\n");
        return enc_outputs;
    }

    MNN::Express::VARP translate_run_decoder(const MNN::Express::VARP& z, MNN::Express::VARP g_expand_outputs) {
        MNNBV2PRINT("Load dec\n");
        auto z_min = MNN::Express::_Const(-1000.f, z->getInfo()->dim, MNN::Express::NCHW);
        auto z_max = MNN::Express::_Const(1000.f, z->getInfo()->dim, MNN::Express::NCHW);
        // just for safe
        auto z_fix = MNN::Express::_Minimum(MNN::Express::_Maximum(z, z_min), z_max);

        MNNBV2PRINT("start forward\n");
        std::vector<MNN::Express::VARP> dec_outputs  = g_dec_module->onForward({z_fix, std::move(g_expand_outputs)});

        auto o = dec_outputs[0];
        MNNBV2PRINT("dec forward finish\n");
        return o;
    }


    MNN::Express::VARP translate_run_dp(int size, MNN::Express::VARP xout, MNN::Express::VARP g_expand_outputs, MNN::Express::VARP x_mask) {
        std::cout << "Initializing DP..." << std::endl;
        MNN::Express::VARP noise_scale_w = MNN::Express::_Scalar<float>(sdp_noise_scale);

        //zin = np.random.randn(x.shape[0], 2, x.shape[2]).astype(np.float32) * noise_scale_w
        auto zin_shape = MNN::Express::_Const(.5f, {1, 2, size}, MNN::Express::NCHW);
        auto zin = MNN::Express::_RandomUnifom(MNN::Express::_Shape(zin_shape, true), halide_type_of<float>(), 0.0f, 1.0f, random_seed_0, random_seed_1) * noise_scale_w;

        MNNBV2PRINT("Load sdp start forward\n");
        std::vector<MNN::Express::VARP> sdp_outputs  = g_sdp_module->onForward({zin, xout, g_expand_outputs, x_mask});
        const auto& logw_sdp = sdp_outputs[0];

        MNNBV2PRINT("Load dp start forward \n");
        std::vector<MNN::Express::VARP> dp_outputs  = g_dp_module->onForward({std::move(xout), std::move(g_expand_outputs), std::move(x_mask)});
        const auto& logw_dp = dp_outputs[0];
        auto dp_ratio = MNN::Express::_Scalar<float>(1 - sdp_ratiof);
        auto sdp_ratio = MNN::Express::_Scalar<float>(sdp_ratiof);
        auto logw = (logw_dp * dp_ratio) + (logw_sdp * sdp_ratio);
        return logw;
    }

    MNN::Express::VARP translate_w_ceil(MNN::Express::VARP logw, MNN::Express::VARP x_mask) {
        // w = torch.exp(logw) * x_mask * length_scale
        // w_ceil = torch.ceil(w)
        auto length_scale = MNN::Express::_Scalar<float>(g_length_scale);
        auto w = MNN::Express::_Exp(std::move(logw)) * std::move(x_mask) * length_scale;
        auto w_ceil = MNN::Express::_Ceil(w);
        return w_ceil;
    }

    int translate_y_length(MNN::Express::VARP w_ceil) {
        // y_lengths = torch.clamp_min(torch.sum(w_ceil, [1, 2]), 1).long()
        auto sum_w_ceil = MNN::Express::_ReduceSum(std::move(w_ceil), {1, 2}, false);
        auto y_lengths = clamp((sum_w_ceil->readMap<float>())[0], 1.0f, 100000.0f);
        MNNBV2PRINT("y_lengths: %f\n", y_lengths);
        return (int)y_lengths;
    }

    MNN::Express::VARP translate_y_mask(int y_lengths) {
        // y_mask = torch.unsqueeze(commons.sequence_mask(y_lengths, None), 1).to(x_mask.dtype)
        auto y_lengths_sequence_mask = MNN::Express::_Const(1.f, {1, (int)y_lengths}, MNN::Express::NCHW);
        auto y_mask = MNN::Express::_Unsqueeze(y_lengths_sequence_mask, {1});
        return y_mask;
    }

    MNN::Express::VARP translate_attn_mask(MNN::Express::VARP x_mask, MNN::Express::VARP y_mask) {
        // attn_mask = torch.unsqueeze(x_mask, 2) * torch.unsqueeze(y_mask, -1)
        auto x_mask_expand = MNN::Express::_Unsqueeze(std::move(x_mask), {2});
        auto y_mask_expand = MNN::Express::_Unsqueeze(std::move(y_mask), {-1});
        auto attn_mask = x_mask_expand * y_mask_expand;
        return attn_mask;
    }

    MNN::Express::VARP translate_attn(MNN::Express::VARP duration, const MNN::Express::VARP& mask) {
        // translate for generate_path func
        //b, _, t_y, t_x = mask.shape
        auto b = mask->getInfo()->dim[0];
        auto t_y = mask->getInfo()->dim[2];
        auto t_x = mask->getInfo()->dim[3];
        // cum_duration = torch.cumsum(duration, -1)
        auto cum_duration = MNN::Express::_CumSum(std::move(duration), -1, false, false);
        print_output_dim("cum_duration", cum_duration);
        print_output_value(10, "cum_duration", cum_duration);

        // cum_duration_flat = cum_duration.view(b * t_x)
        auto cum_duration_flat = MNN::Express::_Reshape(cum_duration, {b * t_x});
        print_output_dim("cum_duration_flat", cum_duration_flat);
        print_output_value(10, "cum_duration_flat", cum_duration_flat);

        // path = sequence_mask(cum_duration_flat, t_y).to(mask.dtype)
        auto range_ = MNN::Express::_Range(
                MNN::Express::_Const(0.0, {1}, MNN::Express::NCHW),
                MNN::Express::_Const((float)t_y, {1}, MNN::Express::NCHW),
                MNN::Express::_Const(1.0 ,{1}, MNN::Express::NCHW));
        //默认出来的是 NHWC, 需要强转
        MNN::Express::VARP t_y_ = MNN::Express::_Convert(range_, MNN::Express::NCHW);
        auto t_y_dim = t_y_->getInfo()->dim[0];
        auto cum_duration_flat_dim = cum_duration_flat->getInfo()->dim[0];
        auto range = _Unsqueeze(t_y_, {0}); // [1, maxLength]
        auto lengthExpanded = _Unsqueeze(cum_duration_flat, {1});
        const std::vector<int> dim_data = {cum_duration_flat_dim, t_y_dim};
        auto shape = MNN::Express::_Const(dim_data.data(), {(int)dim_data.size()}, MNN::Express::NCHW, halide_type_of<int>());
        auto rangeExpanded_ = _Cast(MNN::Express::_BroadcastTo(range, shape), halide_type_of<float>());
        auto lengthExpanded_ = MNN::Express::_BroadcastTo(lengthExpanded, shape);
        // np.expand_dims(x, 0) < np.expand_dims(length, 1)
        auto path = _Cast(_Less(rangeExpanded_, lengthExpanded_), halide_type_of<int>());
        // path = path.reshape(b, t_x, t_y)
        path = _Reshape(path, {b, t_x, t_y});
        // path = path ^ np.pad(path, ((0, 0), (1, 0), (0, 0)))[:, :-1]
        std::vector<int> padVec = {
                0, 0,  // 第 0 维（batch）：不填充
                1, 0,  // 第 1 维（time）：前面 pad 1
                0, 0   // 第 2 维（dim）：不填充
        };
        MNN::Express::VARP pads = _Const(padVec.data(), {3, 2}, MNN::Express::NCHW, halide_type_of<int>());
        auto padded = MNN::Express::_Pad(path, pads,  MNN::Express::CONSTANT);
        const std::vector<int> startIndices = {0, 0, 0};
        MNN::Express::VARP starts = _Const(startIndices.data(), {(int)startIndices.size()}, MNN::Express::NCHW, halide_type_of<int>());
        const std::vector<int> sliceSizes = {padded->getInfo()->dim[0], padded->getInfo()->dim[1] - 1, padded->getInfo()->dim[2]};
        MNN::Express::VARP sizes = _Const(sliceSizes.data(), {(int)sliceSizes.size()}, MNN::Express::NCHW, halide_type_of<int>());
        MNN::Express::VARP result = _Slice(padded, starts, sizes);
        path = _BitwiseXor(_Cast(path,  halide_type_of<int>()), _Cast(result, halide_type_of<int>()));
        path = _Unsqueeze(path, {1});
        path = _Transpose(path, {0, 1, 3, 2});
        return path;
    }

    std::pair<MNN::Express::VARP, MNN::Express::VARP> translate_m_p(MNN::Express::VARP attn, MNN::Express::VARP m_p) {
        MNNBV2PRINT("start m_p\n");
        //   m_p = np.matmul(attn.squeeze(1), m_p.transpose(0, 2, 1)).transpose(0, 2, 1)
        auto m_p_transpose = MNN::Express::_Transpose(std::move(m_p), {0, 2, 1});
        auto attn_squeeze = MNN::Express::_Squeeze(MNN::Express::_Cast(std::move(attn),  halide_type_of<float>()), {1});
        auto attn_m_p_mul = MNN::Express::_MatMul(attn_squeeze, m_p_transpose);
        auto m_p_ = MNN::Express::_Transpose(attn_m_p_mul, {0, 2, 1});
        return {m_p_, attn_squeeze};
    }

    MNN::Express::VARP translate_logs_p(MNN::Express::VARP attn_squeeze, MNN::Express::VARP logs_p) {
        MNNBV2PRINT("start logs_p\n");
        //    logs_p = np.matmul(attn.squeeze(1), logs_p.transpose(0, 2, 1)).transpose(0, 2, 1)
        auto logs_p_transpose = MNN::Express::_Transpose(std::move(logs_p), {0, 2, 1});
        auto logs_p_ = MNN::Express::_Transpose(MNN::Express::_MatMul(std::move(attn_squeeze), logs_p_transpose), {0, 2, 1});
        return logs_p_;
    }

    MNN::Express::VARP translate_z_p(const MNN::Express::VARP& m_p_, MNN::Express::VARP logs_p_) {
        MNNBV2PRINT("start z_p\n");
        // z_p_rand = np.random.randn(m_p.shape[0], m_p.shape[1], m_p.shape[2])
        // z_p = ( m_p + z_p_rand * np.exp(logs_p) * noise_scale)
        auto m_p_shape = MNN::Express::_Shape(m_p_, true);
        auto rand_z_p = MNN::Express::_RandomUnifom(m_p_shape, halide_type_of<float>(), 0.0f, 1.0f, random_seed_0, random_seed_1);
        auto logs_p_exp = MNN::Express::_Exp(std::move(logs_p_));
        auto rand_mul_logs = rand_z_p * logs_p_exp;
        auto shape = rand_mul_logs->getInfo()->dim;
        auto noise_scale = MNN::Express::_Const(seq_noise_scale, shape, MNN::Express::NCHW) ;
        auto z_p = rand_mul_logs * noise_scale + MNN::Express::_Cast(m_p_, halide_type_of<float>());
        return z_p;
    }


    MNN::Express::VARP translate_run_emb(int spkid) {
        // g = self.emb_g(sid).unsqueeze(-1)  # [b, h, 1]
        MNN::Express::VARP sid = MNN::Express::_Scalar<int>(spkid);
        MNNBV2PRINT("Load emb_g start forward\n");
        std::vector<MNN::Express::VARP> emb_g_outputs  = g_emb_module->onForward({sid});
        auto g_expand_outputs = MNN::Express::_Unsqueeze( emb_g_outputs[0], {-1, 0});
        return g_expand_outputs;
    }

    MNN::Express::VARP translate_run_flow(MNN::Express::VARP z_p, MNN::Express::VARP y_mask, MNN::Express::VARP g_expand_outputs) {
        MNNBV2PRINT("Load flow start forward\n");
        std::vector<MNN::Express::VARP> flow_outputs  = g_flow_module->onForward({std::move(z_p), std::move(y_mask), std::move(g_expand_outputs)});
        auto z = flow_outputs[0];
        return z;
    }

    void init_vits_loader() {
        if (g_initialized) {
            MNNBV2PRINT("Vits loader already initialized.\n");
            return;
        }
        MNNBV2PRINT("Vits loader initializing...\n");
        // 创建Exector
        MNN::BackendConfig backendConfig;    // default backend config
        std::shared_ptr<MNN::Express::Executor> executor = MNN::Express::Executor::newExecutor(MNN_FORWARD_CPU, backendConfig, 4);

        // 设置使用4线程+CPU
        executor->setGlobalExecutorConfig(MNN_FORWARD_CPU, backendConfig, 4);

        // 绑定Executor，在创建/销毁/使用Module或进行表达式计算之前都需要绑定
        MNN::Express::ExecutorScope _s(executor);
        MNNBV2PRINT("Executor is finish\n");
        g_initialized = true;
    }

    void destroy_vits_loader() {
        if (!g_initialized) {
            MNNBV2PRINT("Vits loader not initialized.\n");
            return;
        }
        MNNBV2PRINT("Destroying Vits loader...\n");
        g_enc_module.reset();
        g_dec_module.reset();
        g_dp_module.reset();
        g_emb_module.reset();
        g_flow_module.reset();
        g_sdp_module.reset();
        g_bert_module.reset();
        g_initialized = false;
    }

    void set_vits_model_path(
            const std::string &enc_model_path,
            const std::string &dec_model_path,
            const std::string &sdp_model_path,
            const std::string &dp_model_path,
            const std::string &emb_model_path,
            const std::string &flow_model_path,
            const std::string &bert_model_path) {
        MNNBV2PRINT("Set model path: enc_model_path: %s, dec_model_path: %s, dp_model_path: %s, emb_model_path: %s, flow_model_path: %s\n",
                   enc_model_path.c_str(), dec_model_path.c_str(), dp_model_path.c_str(), emb_model_path.c_str(), flow_model_path.c_str());
        MNNBV2PRINT("Set model path: sdp_model_path: %s, bert_model_path: %s\n",
                   sdp_model_path.c_str(), bert_model_path.c_str());

        const std::vector<std::string> input_names_enc_p{"x", "t", "language", "bert_0", "bert_1", "bert_2", "g"};
        const std::vector<std::string> output_names_enc_p{"logs_p", "m_p", "x_mask", "xout"};
        g_enc_module = std::shared_ptr<MNN::Express::Module>(MNN::Express::Module::load(input_names_enc_p, output_names_enc_p, enc_model_path.c_str(), nullptr, &mdconfig));
        const std::vector<std::string> input_names_dec{"z_in", "g"};
        const std::vector<std::string> output_names_dec{"o"};
        g_dec_module = std::shared_ptr<MNN::Express::Module>(MNN::Express::Module::load(input_names_dec, output_names_dec, dec_model_path.c_str(), nullptr, &mdconfig));
        const std::vector<std::string> input_names_sdp{"zin", "x", "g", "x_mask"};
        const std::vector<std::string> output_names_sdp{"logw"};
        g_sdp_module = std::shared_ptr<MNN::Express::Module>(MNN::Express::Module::load(input_names_sdp, output_names_sdp, sdp_model_path.c_str(), nullptr, &mdconfig));
        const std::vector<std::string> input_names_dp{"x", "g", "x_mask"};
        const std::vector<std::string> output_names_dp{"logw"};
        g_dp_module = std::shared_ptr<MNN::Express::Module>(MNN::Express::Module::load(input_names_dp, output_names_dp, dp_model_path.c_str(), nullptr, &mdconfig));
        const std::vector<std::string> input_names_emb_g{"sid"};
        const std::vector<std::string> output_names_emb_g{"g"};
        g_emb_module = std::shared_ptr<MNN::Express::Module>(MNN::Express::Module::load(input_names_emb_g, output_names_emb_g, emb_model_path.c_str(), nullptr, &mdconfig));
        const std::vector<std::string> input_names_flow{"z_p", "y_mask", "g"};
        const std::vector<std::string> output_names_flow{"z"};
        g_flow_module = std::shared_ptr<MNN::Express::Module>(MNN::Express::Module::load(input_names_flow, output_names_flow, flow_model_path.c_str(), nullptr, &mdconfig));
        const std::vector<std::string> input_names_bert{"input_ids", "attention_mask"};
        const std::vector<std::string> output_names_bert{"hidden_state"};
        g_bert_module = std::shared_ptr<MNN::Express::Module>(MNN::Express::Module::load(input_names_bert, output_names_bert, bert_model_path.c_str(), nullptr, &mdconfig));
    }

    void set_length_scale(float scale) {
        g_length_scale = scale;
    }

    std::vector<float> start_audio_infer(const std::vector<int>& _input_seq,
                                         const std::vector<int>& _input_t,
                                         const std::vector<int>& _input_language,
                                         const std::vector<int>& _input_ids,
                                         const std::vector<int>& _input_word2ph,
                                         const std::vector<int>& _attention_mask,
                                         int spkid) {
        MNNBV2PRINT("Starting audio inference... spkid %d\n", spkid);
        auto g_expand_outputs = translate_run_emb(spkid);
        auto bert_outputs = translate_run_bert(_input_ids, _input_word2ph, _attention_mask);
        auto enc_outputs = translate_run_encoder(_input_seq, _input_t, _input_language, bert_outputs[0], bert_outputs[1], bert_outputs[2], g_expand_outputs);
        const auto& logs_p = enc_outputs[0];
        const auto& m_p = enc_outputs[1];
        const auto& x_mask = enc_outputs[2];
        const auto& xout = enc_outputs[3];
        auto logw = translate_run_dp((int)_input_seq.size(), xout, g_expand_outputs, x_mask);
        auto w_ceil = translate_w_ceil(logw, x_mask);
        auto y_lengths = translate_y_length(w_ceil);
        auto y_mask = translate_y_mask(y_lengths);
        auto attn_mask = translate_attn_mask(x_mask, y_mask);
        auto attn = translate_attn(w_ceil, attn_mask);
        auto [m_p_, attn_squeeze] = translate_m_p(attn, m_p);
        auto logs_p_ = translate_logs_p(attn_squeeze, logs_p);
        auto z_p = translate_z_p(m_p_, logs_p_);
        auto z = translate_run_flow(z_p, y_mask, g_expand_outputs);
        auto audio = translate_run_decoder(z, g_expand_outputs);
        auto audio_ptr = audio->readMap<float>();
        auto audio_size = audio->getInfo()->dim[2];
        std::vector<float> audio_vector(audio_ptr, audio_ptr + audio_size);
        MNNBV2PRINT("Audio inference completed.\n");
        return audio_vector;
    }
}