# preprocess_dataset.py

import os
import hanlp
import re
from datasets import load_dataset, Dataset, IterableDatasetDict
from transformers import AutoTokenizer
import pickle
import glob
import random
from itertools import chain
from tqdm import tqdm
from concurrent.futures import ThreadPoolExecutor, as_completed
from collections import Counter
import matplotlib.pyplot as plt
import numpy as np
from multiprocessing import Pool, cpu_count
from hanlp.utils.rules import split_sentence
from itertools import islice

# Configuration
CACHE_PATH = "./cached_dataset.pkl"
MAX_SENTENCE_LENGTH = 128
SAMPLE_SIZE = 1000# skypile 采样数
MODEL_NAME = "E:\\PythonProject\\PythonProject\\chinese-roberta-wwm-ext-large"
SKYPILE_PATH = "E:\\huggingface_data\\datasets--Skywork--SkyPile-150B"
WIKI_ZH_PATH = "E:\\huggingface_data\\datasets--pleisto--wikipedia-cn-20230720-filtered"
USE_SKYPILE = True
USE_WIKI_ZH = True
NUM_THREADS = 8  # 并行线程数
LOG_PATH = "token_length_summary.log"

def load_and_preprocess_dataset():
    if os.path.exists(CACHE_PATH):
        print(f"Loading cached dataset from {CACHE_PATH}...")
        with open(CACHE_PATH, "rb") as f:
            return pickle.load(f)

    # === Load Wikipedia into memory ===
    wiki_sentences = []
    if USE_WIKI_ZH:
        print("Loading and preprocessing Wikipedia dataset...")
        wiki_dataset = load_dataset(WIKI_ZH_PATH, "default", split="train")
        print("Processing Wikipedia")
        wiki_texts = wiki_dataset["completion"]
        wiki_sentences = process_stream_in_batches(wiki_texts, limit=len(wiki_texts), workers=NUM_THREADS)

    # === Load SkyPile streaming  ===
    print("Streaming SkyPile dataset ")
    skypile_sentences = []
    if USE_SKYPILE:
        print("Streaming SkyPile from HuggingFace...")
        skypile_stream = load_dataset(SKYPILE_PATH, split="train", streaming=True)
        text_iter = (sample["text"] for sample in skypile_stream)
        skypile_sentences = process_stream_in_batches(text_iter, limit=SAMPLE_SIZE, workers=NUM_THREADS)

    print(f"Collected {len(wiki_sentences)} from Wikipedia and {len(skypile_sentences)} from SkyPile")

    all_sentences = wiki_sentences + skypile_sentences
    random.shuffle(all_sentences)

    dataset = Dataset.from_dict({"text": all_sentences})

    tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)

    def tokenize_fn(batch):
        return tokenizer(batch["text"], truncation=True, padding="max_length", max_length=MAX_SENTENCE_LENGTH)

    tokenized_dataset = dataset.map(tokenize_fn, batched=True)
    tokenized_dataset.set_format(type='torch', columns=['input_ids', 'attention_mask'])

    # === Generate histogram of token lengths ===
    print("Generating token length histogram...")
    lengths = [len(tokenizer.tokenize(text)) for text in all_sentences[:50000]]  # 采样一部分画图
    counter = Counter(lengths)
    plt.figure(figsize=(10, 6))
    plt.bar(counter.keys(), counter.values())
    plt.xlabel("Token Length")
    plt.ylabel("Frequency")
    plt.title("Distribution of Token Lengths")
    plt.grid(True)
    plt.savefig("token_length_hist.png")
    print("Histogram saved to token_length_hist.png")

    # === Summary statistics ===
    stats = np.array(lengths)
    summary_lines = []
    summary_lines.append("--- Token Length Summary ---")
    summary_lines.append(f"Min: {stats.min()} | Max: {stats.max()} | Mean: {stats.mean():.2f} | Median: {np.median(stats)} | Std: {stats.std():.2f}")

    # === Additional statistics: truncation analysis and buckets ===
    num_truncated = np.sum(stats > MAX_SENTENCE_LENGTH)
    truncation_ratio = num_truncated / len(stats)
    summary_lines.append(f"Truncation: {num_truncated} samples ({truncation_ratio:.2%}) exceed {MAX_SENTENCE_LENGTH} tokens")

    bins = [0, 32, 64, 96, 128, float('inf')]
    labels = ["<32", "32-64", "64-96", "96-128", ">128"]
    bucket_counts = Counter()
    for length in stats:
        for i in range(len(bins) - 1):
            if bins[i] < length <= bins[i + 1]:
                bucket_counts[labels[i]] += 1
                break

    summary_lines.append("--- Bucket Distribution ---")
    for label in labels:
        summary_lines.append(f"{label}: {bucket_counts[label]} samples")

    print("\n".join(summary_lines))
    with open(LOG_PATH, "w", encoding="utf-8") as log_file:
        log_file.write("\n".join(summary_lines))
    print(f"Summary written to {LOG_PATH}")

    print(f"Saving processed dataset to {CACHE_PATH}...")
    with open(CACHE_PATH, "wb") as f:
        pickle.dump(tokenized_dataset, f)

    print("Done. Total tokenized samples:", len(tokenized_dataset))
    return tokenized_dataset


def open_cache_files(pattern):
    files = sorted(glob.glob(pattern))
    for path in files:
        with open(path, 'r', encoding='utf-8') as f:
            for line in f:
                yield line



def process_line(line):
    try:
        sents = split_sentence(line)
        return [s.strip() for s in sents if len(s.strip()) > 10]
    except Exception:
        return []


def process_with_split_sentence(texts, limit=None):
    if limit:
        texts = islice(texts, limit)

    results = []
    for line in tqdm(texts, desc="Splitting Sentences"):
        results.extend(process_line(line))
        if limit and len(results) >= limit:
            break
    return results


def process_stream_in_batches(text_iter, limit=SAMPLE_SIZE, workers=NUM_THREADS):
    batch = list(islice(text_iter, limit))
    results = []
    with ThreadPoolExecutor(max_workers=workers) as executor:
        for res in tqdm(executor.map(process_line, batch), total=len(batch), desc="Parallel Sentence Split"):
            results.extend(res)
            if len(results) >= limit:
                break
    return results



if __name__ == "__main__":
    print("Preparing dataset...")
    tokenized_dataset = load_and_preprocess_dataset()
    print("finished...")
