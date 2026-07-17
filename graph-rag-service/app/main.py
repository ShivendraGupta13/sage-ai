from fastapi import FastAPI

app = FastAPI(
    title="Graph RAG Service",
    description="Retrieval and ingestion service for Sage AI",
    version="0.1.0",
)


@app.get("/health")
async def health():
    return {
        "status": "ok"
    }