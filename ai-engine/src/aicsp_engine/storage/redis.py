class RedisStore:
    def __init__(self, url: str | None) -> None:
        self.url = url

    def is_configured(self) -> bool:
        return bool(self.url)
