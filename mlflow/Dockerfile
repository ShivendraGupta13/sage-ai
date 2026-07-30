FROM ghcr.io/mlflow/mlflow:v3.14.0

# Add psycopg2 for PostgreSQL backend support
# and boto3 for S3-compatible artifact stores (RustFS/MinIO)
RUN pip install --no-cache-dir psycopg2-binary boto3
