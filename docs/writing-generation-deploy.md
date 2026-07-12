# Writing Generation Deployment Notes

Run this MySQL incremental patch before first use of the pure writing generation feature:

```bash
mysql -u "$SPRING_DATASOURCE_USERNAME" -p "$MYSQL_DATABASE" < sql/patches/20260712_add_writing_generation.sql
```

Reference search is mandatory by default. Configure:

```text
DOUBAO_WEB_SEARCH_ENABLED=true
WRITING_REFERENCE_SEARCH_ENABLED=true
WRITING_REFERENCE_SEARCH_PROVIDER=doubao,openalex,crossref
WRITING_REFERENCE_SEARCH_MAX_RESULTS=30
WRITING_REFERENCE_SEARCH_TIMEOUT_SECONDS=30
WRITING_REFERENCE_SEARCH_RETRY_COUNT=3
WRITING_MAX_RETRY=3
WRITING_CHAPTER_TIMEOUT=180
```

If Doubao web search is not available in the current deployment, DropAI falls back to real OpenAlex and Crossref requests. API keys must come from environment variables or the existing configuration system.
