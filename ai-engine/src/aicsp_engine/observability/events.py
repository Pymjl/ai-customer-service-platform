from dataclasses import dataclass


@dataclass(slots=True)
class IngestionMetrics:
    document_id: str
    source_type: str
    split_strategy: str
    case_count: int = 0
    parent_chunk_count: int = 0
    child_chunk_count: int = 0
    hard_split_count: int = 0
