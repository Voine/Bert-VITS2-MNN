package com.example.bertvits2mnn.preprocess
import com.example.cppjieba.CppJiebaJNI
import com.github.houbb.pinyin.constant.enums.PinyinStyleEnum
import com.github.houbb.pinyin.util.PinyinHelper

/**
 * Author: Voine
 * Date: 2025/4/17
 * Description: translate for tone_sandhi.py
 * 大部分由 GPT 生成，调整了一下结构但没太仔细 check 细节是否准确，希望不要有问题
 */
class ToneSandhi(val jiebaJNI: CppJiebaJNI) {
    // 对应 Python 中的 must_neural_tone_words 和 must_not_neural_tone_words
    private val mustNeuralToneWords: Set<String> = setOf(
        "麻烦",
        "麻利",
        "鸳鸯",
        "高粱",
        "骨头",
        "骆驼",
        "马虎",
        "首饰",
        "馒头",
        "馄饨",
        "风筝",
        "难为",
        "队伍",
        "阔气",
        "闺女",
        "门道",
        "锄头",
        "铺盖",
        "铃铛",
        "铁匠",
        "钥匙",
        "里脊",
        "里头",
        "部分",
        "那么",
        "道士",
        "造化",
        "迷糊",
        "连累",
        "这么",
        "这个",
        "运气",
        "过去",
        "软和",
        "转悠",
        "踏实",
        "跳蚤",
        "跟头",
        "趔趄",
        "财主",
        "豆腐",
        "讲究",
        "记性",
        "记号",
        "认识",
        "规矩",
        "见识",
        "裁缝",
        "补丁",
        "衣裳",
        "衣服",
        "衙门",
        "街坊",
        "行李",
        "行当",
        "蛤蟆",
        "蘑菇",
        "薄荷",
        "葫芦",
        "葡萄",
        "萝卜",
        "荸荠",
        "苗条",
        "苗头",
        "苍蝇",
        "芝麻",
        "舒服",
        "舒坦",
        "舌头",
        "自在",
        "膏药",
        "脾气",
        "脑袋",
        "脊梁",
        "能耐",
        "胳膊",
        "胭脂",
        "胡萝",
        "胡琴",
        "胡同",
        "聪明",
        "耽误",
        "耽搁",
        "耷拉",
        "耳朵",
        "老爷",
        "老实",
        "老婆",
        "老头",
        "老太",
        "翻腾",
        "罗嗦",
        "罐头",
        "编辑",
        "结实",
        "红火",
        "累赘",
        "糨糊",
        "糊涂",
        "精神",
        "粮食",
        "簸箕",
        "篱笆",
        "算计",
        "算盘",
        "答应",
        "笤帚",
        "笑语",
        "笑话",
        "窟窿",
        "窝囊",
        "窗户",
        "稳当",
        "稀罕",
        "称呼",
        "秧歌",
        "秀气",
        "秀才",
        "福气",
        "祖宗",
        "砚台",
        "码头",
        "石榴",
        "石头",
        "石匠",
        "知识",
        "眼睛",
        "眯缝",
        "眨巴",
        "眉毛",
        "相声",
        "盘算",
        "白净",
        "痢疾",
        "痛快",
        "疟疾",
        "疙瘩",
        "疏忽",
        "畜生",
        "生意",
        "甘蔗",
        "琵琶",
        "琢磨",
        "琉璃",
        "玻璃",
        "玫瑰",
        "玄乎",
        "狐狸",
        "状元",
        "特务",
        "牲口",
        "牙碜",
        "牌楼",
        "爽快",
        "爱人",
        "热闹",
        "烧饼",
        "烟筒",
        "烂糊",
        "点心",
        "炊帚",
        "灯笼",
        "火候",
        "漂亮",
        "滑溜",
        "溜达",
        "温和",
        "清楚",
        "消息",
        "浪头",
        "活泼",
        "比方",
        "正经",
        "欺负",
        "模糊",
        "槟榔",
        "棺材",
        "棒槌",
        "棉花",
        "核桃",
        "栅栏",
        "柴火",
        "架势",
        "枕头",
        "枇杷",
        "机灵",
        "本事",
        "木头",
        "木匠",
        "朋友",
        "月饼",
        "月亮",
        "暖和",
        "明白",
        "时候",
        "新鲜",
        "故事",
        "收拾",
        "收成",
        "提防",
        "挖苦",
        "挑剔",
        "指甲",
        "指头",
        "拾掇",
        "拳头",
        "拨弄",
        "招牌",
        "招呼",
        "抬举",
        "护士",
        "折腾",
        "扫帚",
        "打量",
        "打算",
        "打点",
        "打扮",
        "打听",
        "打发",
        "扎实",
        "扁担",
        "戒指",
        "懒得",
        "意识",
        "意思",
        "情形",
        "悟性",
        "怪物",
        "思量",
        "怎么",
        "念头",
        "念叨",
        "快活",
        "忙活",
        "志气",
        "心思",
        "得罪",
        "张罗",
        "弟兄",
        "开通",
        "应酬",
        "庄稼",
        "干事",
        "帮手",
        "帐篷",
        "希罕",
        "师父",
        "师傅",
        "巴结",
        "巴掌",
        "差事",
        "工夫",
        "岁数",
        "屁股",
        "尾巴",
        "少爷",
        "小气",
        "小伙",
        "将就",
        "对头",
        "对付",
        "寡妇",
        "家伙",
        "客气",
        "实在",
        "官司",
        "学问",
        "学生",
        "字号",
        "嫁妆",
        "媳妇",
        "媒人",
        "婆家",
        "娘家",
        "委屈",
        "姑娘",
        "姐夫",
        "妯娌",
        "妥当",
        "妖精",
        "奴才",
        "女婿",
        "头发",
        "太阳",
        "大爷",
        "大方",
        "大意",
        "大夫",
        "多少",
        "多么",
        "外甥",
        "壮实",
        "地道",
        "地方",
        "在乎",
        "困难",
        "嘴巴",
        "嘱咐",
        "嘟囔",
        "嘀咕",
        "喜欢",
        "喇嘛",
        "喇叭",
        "商量",
        "唾沫",
        "哑巴",
        "哈欠",
        "哆嗦",
        "咳嗽",
        "和尚",
        "告诉",
        "告示",
        "含糊",
        "吓唬",
        "后头",
        "名字",
        "名堂",
        "合同",
        "吆喝",
        "叫唤",
        "口袋",
        "厚道",
        "厉害",
        "千斤",
        "包袱",
        "包涵",
        "匀称",
        "勤快",
        "动静",
        "动弹",
        "功夫",
        "力气",
        "前头",
        "刺猬",
        "刺激",
        "别扭",
        "利落",
        "利索",
        "利害",
        "分析",
        "出息",
        "凑合",
        "凉快",
        "冷战",
        "冤枉",
        "冒失",
        "养活",
        "关系",
        "先生",
        "兄弟",
        "便宜",
        "使唤",
        "佩服",
        "作坊",
        "体面",
        "位置",
        "似的",
        "伙计",
        "休息",
        "什么",
        "人家",
        "亲戚",
        "亲家",
        "交情",
        "云彩",
        "事情",
        "买卖",
        "主意",
        "丫头",
        "丧气",
        "两口",
        "东西",
        "东家",
        "世故",
        "不由",
        "不在",
        "下水",
        "下巴",
        "上头",
        "上司",
        "丈夫",
        "丈人",
        "一辈",
        "那个",
        "菩萨",
        "父亲",
        "母亲",
        "咕噜",
        "邋遢",
        "费用",
        "冤家",
        "甜头",
        "介绍",
        "荒唐",
        "大人",
        "泥鳅",
        "幸福",
        "熟悉",
        "计划",
        "扑腾",
        "蜡烛",
        "姥爷",
        "照顾",
        "喉咙",
        "吉他",
        "弄堂",
        "蚂蚱",
        "凤凰",
        "拖沓",
        "寒碜",
        "糟蹋",
        "倒腾",
        "报复",
        "逻辑",
        "盘缠",
        "喽啰",
        "牢骚",
        "咖喱",
        "扫把",
        "惦记",
    )
    private val mustNotNeuralToneWords: Set<String> = setOf(
        "男子", "女子", "分子", "原子", "量子", "莲子", "石子", "瓜子", "电子", "人人", "虎虎"
    )
    private val punc = setOf<Char>('：', '，', '；', '。', '？', '！', '“', '”', '‘', '’', '\'', ':', ',', ';', '.', '?', '!', '"')

    /**
     * 主入口：按顺序应用预合并、不、“一”、神经、三声变调
     */
    fun preMergeForModify(seg: List<WordPos>): List<WordPos> {
        val buMerged = mergeBu(seg)
        val yiMerged = mergeYi(buMerged)
        val redupMerged = mergeReduplication(yiMerged)
        val triple1 = mergeContinuousThreeTones(redupMerged)
        val triple2 = mergeContinuousThreeTonesAlt(triple1)
        return mergeEr(triple2)
    }

    fun modifiedTone(word: String, pos: String, finals: List<String>): List<String> {
        val f = finals.toMutableList()
        applyBuRule(word, f)
        applyYiRule(word, f)
        applyNeuralRule(word, pos, f)
        applyThreeToneRule(word, f)
        return f
    }

    /**
     * "不" 变调规则
     */
    private fun applyBuRule(word: String, finals: MutableList<String>) {
        if (word.length == 3 && word[1] == '不') {
            finals[1] = finals[1].dropLast(1) + "5"
        } else {
            for (i in word.indices) {
                if (word[i] == '不' && i + 1 < finals.size && finals[i + 1].last() == '4') {
                    finals[i] = finals[i].dropLast(1) + "2"
                }
            }
        }
    }

    /**
     * "一" 变调规则
     */
    private fun applyYiRule(word: String, finals: MutableList<String>) {
        // 数字序列中的 "一"
        if (word.contains('一') && word.filter { it != '一' }.all { it.isDigit() }) return

        for (i in word.indices) {
            if (word[i] == '一') {
                // 重叠型 e.g. 看一看
                if (word.length == 3 && word[1] == '一' && word[0] == word[2]) {
                    finals[1] = finals[1].dropLast(1) + "5"
                }
                // 序数第一
                else if (word.startsWith("第一") && i == 1) {
                    finals[i] = finals[i].dropLast(1) + "1"
                }
                // 通用规则
                else if (i + 1 < finals.size) {
                    val next = word[i + 1]
                    finals[i] = when {
                        finals[i + 1].last() == '4' -> finals[i].dropLast(1) + "2"
                        next !in punc -> finals[i].dropLast(1) + "4"
                        else -> finals[i]  // 遇到标点不变
                    }
                }
            }
        }
    }

    /**
     * 神经变调规则，对应 Python 中 _neural_sandhi
     */
    private fun applyNeuralRule(word: String, pos: String, finals: MutableList<String>) {
        // 重叠词 n/v/a
        for (j in word.indices) {
            if (j - 1 >= 0
                && word[j] == word[j - 1]
                && pos.first() in setOf('n', 'v', 'a')
                && word !in mustNotNeuralToneWords
            ) {
                finals[j] = finals[j].dropLast(1) + "5"
            }
        }
        // 语气助词
        word.lastOrNull()?.let { last ->
            if ("吧呢啊呐噻嘛吖嗨呐哦哒额滴哩哟喽啰耶喔诶".contains(last)) {
                finals[finals.lastIndex] = finals.last().dropLast(1) + "5"
            }
        }
        // 结构助词 的地得
        if (word.endsWith("的") || word.endsWith("地") || word.endsWith("得")) {
            finals[finals.lastIndex] = finals.last().dropLast(1) + "5"
        }
        // 们、子
        if (word.length > 1
            && word.last() in listOf('们', '子')
            && pos in setOf("r", "n")
            && word !in mustNotNeuralToneWords
        ) {
            finals[finals.lastIndex] = finals.last().dropLast(1) + "5"
        }
        // 上下里
        if (word.length > 1
            && word.last() in listOf('上', '下', '里')
            && pos in setOf("s", "l", "f")
        ) {
            finals[finals.lastIndex] = finals.last().dropLast(1) + "5"
        }
        // 来去 前置字
        if (word.length > 1
            && word.last() in listOf('来', '去')
            && word[word.length - 2] in listOf('上', '下', '进', '出', '回', '过', '起', '开')
        ) {
            finals[finals.lastIndex] = finals.last().dropLast(1) + "5"
        }
        // 个量词
        val geIdx = word.indexOf('个')
        if ((geIdx >= 1
                    && (word[geIdx - 1].isDigit() || "几有两半多各整每做是".contains(word[geIdx - 1])))
            || word == "个"
        ) {
            finals[geIdx] = finals[geIdx].dropLast(1) + "5"
        } else if (word in mustNeuralToneWords || word.takeLast(2) in mustNeuralToneWords) {
            finals[finals.lastIndex] = finals.last().dropLast(1) + "5"
        }
        // 分词后再检查子词
        val parts = splitWord(word)
        val leftLen = parts[0].length
        val first = finals.subList(0, leftLen).toMutableList()
        val second = finals.subList(leftLen, finals.size).toMutableList()
        listOf(first, second).forEachIndexed { idx, part ->
            if (parts[idx] in mustNeuralToneWords || parts[idx].takeLast(2) in mustNeuralToneWords) {
                val lastIdx = part.lastIndex
                part[lastIdx] = part[lastIdx].dropLast(1) + "5"
            }
        }
        finals.clear()
        finals.addAll(first)
        finals.addAll(second)
    }

    /**
     * 三声变调规则，对应 Python 中 _three_sandhi
     */
    private fun applyThreeToneRule(word: String, finals: MutableList<String>) {
        // 两字全三声
        if (finals.size == 2 && finals.all { it.last() == '3' }) {
            finals[0] = finals[0].dropLast(1) + "2"
            return
        }
        // 三字
        if (finals.size == 3) {
            if (allToneThree(finals)) {
                val parts = splitWord(word)
                when (parts[0].length) {
                    2 -> {
                        finals[0] = finals[0].dropLast(1) + "2"
                        finals[1] = finals[1].dropLast(1) + "2"
                    }
                    1 -> {
                        finals[1] = finals[1].dropLast(1) + "2"
                    }
                }
            } else {
                // 保持默认，可根据需要扩展 Python 中其他子场景逻辑
            }
            return
        }
        // 四字成语
        if (finals.size == 4) {
            val left = finals.subList(0, 2).toMutableList()
            val right = finals.subList(2, 4).toMutableList()
            if (allToneThree(left)) left[0] = left[0].dropLast(1) + "2"
            if (allToneThree(right)) right[0] = right[0].dropLast(1) + "2"
            finals.clear()
            finals.addAll(left)
            finals.addAll(right)
        }
    }

    private fun allToneThree(finals: List<String>): Boolean = finals.all { it.last() == '3' }

    /**
     * 分词接口，替代 Python jieba.cut_for_search
     */
    private fun splitWord(word: String): List<String> {
        val cuts = jiebaSplitForSearch(word)
        val sorted = cuts.sortedBy { it.length }
        val first = sorted.first()
        val idx = word.indexOf(first)
        return if (idx == 0) listOf(first, word.substring(first.length))
        else listOf(word.substring(0, word.length - first.length), first)
    }

    /**
     * 预合并：连续三声合并（规则1）
     */
    private fun mergeContinuousThreeTones(seg: List<WordPos>): List<WordPos> {
        val pinyins = seg.map { lazyPinyin(it.word) }
        val merged = mutableListOf<WordPos>()
        val used = BooleanArray(seg.size)
        for (i in seg.indices) {
            if (i > 0 && allToneThree(pinyins[i - 1]) && allToneThree(pinyins[i]) && !used[i - 1]) {
                val prev = merged.removeAt(merged.lastIndex)
                if (!isReduplication(prev.word) && prev.word.length + seg[i].word.length <= 3) {
                    merged.add(WordPos(prev.word + seg[i].word, prev.pos))
                    used[i] = true
                    continue
                } else {
                    merged.add(seg[i])
                }
            } else if (!used[i]) {
                merged.add(seg[i])
            }
        }
        return merged
    }

    /**
     * 预合并：连续三声合并（规则2）
     */
    private fun mergeContinuousThreeTonesAlt(seg: List<WordPos>): List<WordPos> {
        val pinyins = seg.map { lazyPinyin(it.word) }
        val merged = mutableListOf<WordPos>()
        val used = BooleanArray(seg.size)
        for (i in seg.indices) {
            if (i > 0 && pinyins[i - 1].last().last() == '3' && pinyins[i].first().last() == '3' && !used[i - 1]) {
                val prev = merged[merged.lastIndex]
                if (!isReduplication(prev.word) && prev.word.length + seg[i].word.length <= 3) {
                    val prev = merged.removeAt(merged.lastIndex)
                    merged.add(WordPos(prev.word + seg[i].word, prev.pos))
                    used[i] = true
                } else {
                    merged.add(seg[i])
                }
            } else if (!used[i]) {
                merged.add(seg[i])
            }
        }
        return merged
    }

    private fun isReduplication(word: String): Boolean = word.length == 2 && word[0] == word[1]

    /**
     * 预合并：儿化
     */
    private fun mergeEr(seg: List<WordPos>): List<WordPos> {
        val result = mutableListOf<WordPos>()
        for (i in seg.indices) {
            if (i > 0 && seg[i].word == "儿" && seg[i - 1].word != "#") {
                val prev = result.removeAt(result.lastIndex)
                result.add(WordPos(prev.word + seg[i].word, prev.pos))
            } else {
                result.add(seg[i])
            }
        }
        return result
    }

    /**
     * 预合并：重叠词
     */
    private fun mergeReduplication(seg: List<WordPos>): List<WordPos> {
        val result = mutableListOf<WordPos>()
        for (item in seg) {
            if (result.isNotEmpty() && result.last().word == item.word) {
                val last = result.removeAt(result.lastIndex)
                result.add(WordPos(last.word + item.word, last.pos))
            } else {
                result.add(item)
            }
        }
        return result
    }

    /**
     * 预合并：“一” 合并
     */
    private fun mergeYi(seg: List<WordPos>): List<WordPos> {
        val result = mutableListOf<WordPos>()
        var i = 0
        while (i < seg.size) {
            if (i > 0 && i + 1 < seg.size && seg[i].word == "一" && seg[i - 1].word == seg[i + 1].word && seg[i - 1].pos == "v") {
                val newWord = seg[i - 1].word + "一" + seg[i + 1].word
                result.removeAt(result.lastIndex)
                result.add(WordPos(newWord, "v"))
                i += 2
            } else {
                result.add(seg[i])
                i++
            }
        }
        return result
    }

    /**
     * 预合并：“不” 合并
     */
    private fun mergeBu(seg: List<WordPos>): List<WordPos> {
        val result = mutableListOf<WordPos>()
        var last: WordPos? = null
        for (current in seg) {
            if (last?.word == "不") {
                result.removeAt(result.lastIndex)
                result.add(WordPos(last.word + current.word, current.pos))
            } else {
                result.add(current)
            }
            last = current
        }
        if (last?.word == "不") {
            result.removeAt(result.lastIndex)
            result.add(WordPos(last.word, "d"))
        }
        return result
    }

    fun jiebaSplitForSearch(word: String): List<String> = jiebaJNI.cut(word).toList()

    fun lazyPinyin(word: String): List<String> {
        if (word.trim().length == 1 && punc.contains(word.trim().first())) {
            //标点符号直接返回
            return listOf(word.trim())
        }
        //"返回每个字的拼音 finals tone3 列表，例如 ["ia1","i3"] 格式的 List"
        return PinyinHelper.toPinyin(word, PinyinStyleEnum.NUM_LAST)
            .replace('ü', 'v')
            .split(" ")
            .map { it.drop(1) }
            .toList()
    }
}
