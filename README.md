# shoplab-retrieval-service

La base de conocimiento de ShopLab Helpdesk: documentos, sus vectores y la búsqueda sobre ellos. Salió del módulo
`retrieval` del monolito (LAB-129, ADR-0031).

## API

| Operación | Qué hace |
|---|---|
| `POST /api/documents` | sube un PDF o Markdown (máximo 10 MiB), lo trocea `ingest` y se indexa |
| `GET /api/documents` | lista los documentos y su estado de indexado |
| `GET /api/knowledge/search?query=&limit=` | búsqueda por similitud, sin umbral |

El contrato está en `shoplab-contracts` (`openapi/retrieval/v1`). Los stubs que usan los consumidores son
`com.shoplab:shoplab-retrieval-service:<versión>:stubs`. No hay autenticación hasta que exista el login (LAB-61).

## Dependencias

| Qué | Variable | Por defecto |
|---|---|---|
| PostgreSQL con pgvector, base propia | `POSTGRES_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | `localhost:5432/retrieval` |
| `ingest` | `SHOPLAB_RETRIEVAL_INGEST_BASE_URL` | `http://localhost:8000` |
| Gateway de modelos | `SPRING_AI_OPENAI_BASE_URL`, `SPRING_AI_OPENAI_API_KEY` | `http://localhost:4000/v1` |
| Modelo detrás de `embeddings` | `SHOPLAB_RETRIEVAL_EMBEDDING_MODEL_NAME` | `bge-m3` |

`SHOPLAB_RETRIEVAL_EMBEDDING_MODEL_NAME` debe ser el modelo de los vectores guardados; si no, la búsqueda responde
vacío. El esquema `retrieval` se migra al arrancar y la extensión `vector` debe poder crearse o existir ya.

## Construir y probar

```bash
./mvnw clean verify                 # necesita Docker (Testcontainers)
docker build -t retrieval-service .
```

## Publicar

`release-please` abre el PR de release; al fusionarlo se crea el tag, que publica la imagen y la despliega, y la
release, que publica los stubs.
