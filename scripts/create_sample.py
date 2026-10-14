import argparse
from pathlib import Path


def create_sample(
    input_path: Path,
    output_path: Path,
    target_size_mb: int,
) -> None:
    target_bytes = target_size_mb * 1024 * 1024

    if not input_path.is_file():
        raise FileNotFoundError(f"Input file not found: {input_path}")

    if input_path.resolve() == output_path.resolve():
        raise ValueError("Input and output paths must be different")

    if output_path.exists():
        raise FileExistsError(f"Output file already exists: {output_path}")

    output_path.parent.mkdir(parents=True, exist_ok=True)

    total_bytes = 0
    total_documents = 0

    with input_path.open("rb") as source:
        with output_path.open("wb") as destination:
            for line in source:
                line_size = len(line)

                if total_bytes + line_size > target_bytes:
                    break

                destination.write(line)

                total_bytes += line_size
                total_documents += 1

    print(f"Input: {input_path}")
    print(f"Output: {output_path}")
    print(f"Documents: {total_documents:,}")
    print(f"Size: {total_bytes / (1024 ** 2):.2f} MiB")
    print(f"Target: {target_size_mb} MiB")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Create a size-limited TSV sample"
    )

    parser.add_argument(
        "--input",
        type=Path,
        required=True,
        help="Source TSV file",
    )

    parser.add_argument(
        "--output",
        type=Path,
        required=True,
        help="Output TSV file",
    )

    parser.add_argument(
        "--size-mb",
        type=int,
        required=True,
        help="Maximum output size in MiB",
    )

    args = parser.parse_args()

    if args.size_mb <= 0:
        parser.error("--size-mb must be positive")

    create_sample(
        input_path=args.input,
        output_path=args.output,
        target_size_mb=args.size_mb,
    )