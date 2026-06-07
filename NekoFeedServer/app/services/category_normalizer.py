from sqlalchemy.orm import Session

from app.models import FeedItem, UpstreamFeed


VALID_CATEGORIES = frozenset({"tech", "news", "ad", "local"})

_CATEGORY_ALIASES = {
    "科技": "tech",
    "数码": "tech",
    "技术": "tech",
    "ai": "tech",
    "人工智能": "tech",
    "资讯": "news",
    "新闻": "news",
    "business": "news",
    "商业": "news",
    "财经": "news",
    "生活": "local",
    "本地": "local",
    "本地生活": "local",
    "广告": "ad",
}


def normalize_category(value: str | None, default: str = "tech") -> str:
    normalized = (value or "").strip().lower()
    normalized = _CATEGORY_ALIASES.get(normalized, normalized)
    return normalized if normalized in VALID_CATEGORIES else default


def normalize_item_category(
    value: str | None,
    item_type: str | None,
    is_promotional: bool = False,
) -> str:
    normalized = normalize_category(value)
    if is_promotional and normalized != "local":
        return "ad"
    return normalized


def migrate_categories(db: Session) -> int:
    changed = 0
    for feed in db.query(UpstreamFeed).all():
        normalized = normalize_category(feed.category)
        if feed.category != normalized:
            feed.category = normalized
            changed += 1
    for item in db.query(FeedItem).all():
        normalized = normalize_item_category(
            item.category,
            item.item_type,
            bool(item.is_custom or item.is_sponsored),
        )
        if item.category != normalized:
            item.category = normalized
            changed += 1
    if changed:
        db.commit()
    return changed
