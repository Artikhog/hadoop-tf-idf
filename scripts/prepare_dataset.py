import argparse
import csv
from pathlib import Path

import pyarrow.parquet as pq


def convert_parquet_to_tsv(
    input_path: Path,
    output_path: Path,
    batch_size: int = 1000,
):
    parquet = pq.ParquetFile(input_path)

    total_documents = 0

    with output_path.open("w", encoding="utf-8", newline="") as output:
        writer = csv.writer(
            output,
            delimiter="\t",
            lineterminator="\n",
        )

        for batch in parquet.iter_batches(
            batch_size=batch_size,
            columns=["id", "text"],
        ):
            documents = batch.to_pylist()

            for document in documents:
                document_id = document["id"]
                text = document["text"]

                if not document_id or not text:
                    continue

                # Один документ = одна физическая строка.
                text = " ".join(text.split())

                writer.writerow([document_id, text])

                total_documents += 1

                if total_documents % 10_000 == 0:
                    print(f"Processed {total_documents} documents")

    print(f"Done. Documents: {total_documents}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()

    parser.add_argument(
        "--input",
        type=Path,
        required=True,
    )

    parser.add_argument(
        "--output",
        type=Path,
        required=True,
    )

    args = parser.parse_args()

    convert_parquet_to_tsv(
        input_path=args.input,
        output_path=args.output,
    )