import httpx
import json
import logging
import asyncio
from sqlalchemy.orm import Session
from app.models import FeedItem
from app.services.llm_config import LlmConfig

logger = logging.getLogger(__name__)

# Global progress status
enrichment_status = {
    "running": False,
    "total": 0,
    "processed": 0,
    "success": 0,
    "failed": 0,
    "last_error": None,
}

SYSTEM_PROMPT = """你是一个内容分析与分类助手。给定一篇信息流内容，请分析并输出 JSON。

你需要完成以下任务：
1. **分类** (item_type)：从以下选项中选择最合适的类型
   - "article": 普通文章/新闻/博客
   - "video": 视频内容（含视频链接、视频评测、视频教程等）
   - "product": 商品/产品推荐/评测/开箱（重点在某个具体产品）
   - "local": 本地生活/探店/美食/线下活动
   - "ad": 广告/赞助内容

2. **卡片样式** (card_type)：
   - "large_image": 大图卡片（适合视觉内容为主的，或者有代表性图片的）
   - "small_image": 小图卡片（适合文字为主、配图为辅的）
   - "video": 视频卡片（仅当 item_type 为 video 时）
   - "product": 商品卡片（仅当 item_type 为 product 时）
   - "text_only": 纯文字（无图片时）

3. **标签** (tags)：提取 3-5 个关键标签（用逗号或列表形式）
4. **摘要** (summary)：一句话中文摘要（不超过 50 字）
5. **推荐理由** (reason)：为什么值得阅读的推荐理由（不超过 30 字）
6. **广告与商品特有字段**（非此类型则为 null）：
   - "brand": 如涉及品牌/公司则提取（如 "Apple", "星巴克"），否则 null
   - "cta_text": 广告行动呼吁文本（仅限广告类型，如 "立即抢购", "了解详情"），否则 null
   - "price_text": 商品价格或优惠文本（如 "￥59起", "买一送一"），否则 null
   - "is_sponsored": 是否是赞助/广告内容 (Boolean: true/false)

请严格输出 JSON 格式（不要输出任何 Markdown 格式代码块，只输出纯 JSON）：
{
  "item_type": "article",
  "card_type": "large_image",
  "tags": ["标签1", "标签2"],
  "summary": "一句话摘要",
  "reason": "推荐理由",
  "brand": null,
  "cta_text": null,
  "price_text": null,
  "is_sponsored": false
}"""

def extract_json(text: str) -> dict | None:
    text = text.strip()
    if not text:
        return None
    # Remove markdown code blocks if present
    if text.startswith("```"):
        lines = text.split("\n")
        if lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        text = "\n".join(lines).strip()
    
    # In case there is text before/after the JSON
    start_idx = text.find("{")
    end_idx = text.rfind("}")
    if start_idx != -1 and end_idx != -1:
        text = text[start_idx:end_idx+1]
        
    try:
        return json.loads(text)
    except Exception:
        return None

async def call_llm(config: LlmConfig, item: FeedItem) -> dict | None:
    """Call OpenAI-compatible API to analyze a single FeedItem."""
    user_prompt = f"""标题：{item.title}
来源：{item.source_name or '未知'}
分类：{item.category or '未知'}
摘要：{(item.summary or '无')[:300]}
有图片：{'是' if item.image_url else '否'}
有视频：{'是' if item.media_url else '否'}
原文链接：{item.source_url or '无'}"""

    payload = {
        "model": config.model,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_prompt},
        ],
        "max_tokens": config.max_tokens,
        "temperature": config.temperature,
    }

    url = config.base_url.rstrip("/")
    if not url.endswith("/v1"):
        url += "/v1"
    url += "/chat/completions"

    headers = {"Content-Type": "application/json"}
    if config.api_key:
        headers["Authorization"] = f"Bearer {config.api_key}"

    async with httpx.AsyncClient(timeout=config.timeout) as client:
        resp = await client.post(url, json=payload, headers=headers)
        resp.raise_for_status()
        data = resp.json()
        content = data["choices"][0]["message"]["content"]
        return extract_json(content)

async def test_llm_connection(config: LlmConfig) -> bool:
    """Test connection to the LLM endpoint with a minimal request."""
    payload = {
        "model": config.model,
        "messages": [
            {"role": "user", "content": "ping"}
        ],
        "max_tokens": 10
    }
    url = config.base_url.rstrip("/")
    if not url.endswith("/v1"):
        url += "/v1"
    url += "/chat/completions"

    headers = {"Content-Type": "application/json"}
    if config.api_key:
        headers["Authorization"] = f"Bearer {config.api_key}"

    async with httpx.AsyncClient(timeout=10.0) as client:
        resp = await client.post(url, json=payload, headers=headers)
        resp.raise_for_status()
        return resp.status_code == 200

async def enrich_items(db_session_factory, item_ids: list[str], config: LlmConfig):
    """Background task: enrich the selected feed items using the LLM config."""
    global enrichment_status
    
    enrichment_status = {
        "running": True,
        "total": len(item_ids),
        "processed": 0,
        "success": 0,
        "failed": 0,
        "last_error": None,
    }

    db = db_session_factory()
    try:
        for item_id in item_ids:
            # Check if job was cancelled
            if not enrichment_status["running"]:
                break
                
            item = db.query(FeedItem).filter(FeedItem.id == item_id).first()
            if not item:
                enrichment_status["processed"] += 1
                continue

            try:
                result = await call_llm(config, item)
                if result:
                    # Update category and type fields if valid
                    new_item_type = result.get("item_type", item.item_type)
                    if new_item_type in ("article", "video", "product", "local", "ad"):
                        item.item_type = new_item_type
                    
                    new_card_type = result.get("card_type", item.card_type)
                    if new_card_type in ("large_image", "small_image", "video", "product", "text_only"):
                        item.card_type = new_card_type
                    
                    # Update AI fields
                    tags = result.get("tags", [])
                    item.ai_summary = result.get("summary")
                    item.ai_tags = ",".join(tags) if tags else None
                    item.ai_reason = result.get("reason")
                    item.brand = result.get("brand") or item.brand
                    item.cta_text = result.get("cta_text") or item.cta_text
                    item.price_text = result.get("price_text") or item.price_text
                    item.is_sponsored = result.get("is_sponsored", False) or item.is_sponsored
                    
                    # Fallback original tags if they were empty
                    if not item.tags and tags:
                        item.tags = ",".join(tags)
                        
                    item.ai_enriched = True
                    db.commit()
                    enrichment_status["success"] += 1
                else:
                    raise ValueError("Failed to parse JSON response from LLM")
            except Exception as e:
                logger.error(f"LLM enrichment failed for {item_id}: {e}")
                enrichment_status["failed"] += 1
                enrichment_status["last_error"] = f"Item {item_id}: {str(e)}"

            enrichment_status["processed"] += 1
            # Add a small delay between requests to be polite to the LLM API
            await asyncio.sleep(0.5)
    finally:
        db.close()
        enrichment_status["running"] = False
