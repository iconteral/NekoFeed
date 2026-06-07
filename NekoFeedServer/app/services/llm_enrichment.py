import httpx
import json
import logging
import asyncio
import traceback
from sqlalchemy.orm import Session
from app.models import FeedItem
from app.services.category_normalizer import normalize_category, normalize_item_category
from app.services.llm_config import LlmConfig

logger = logging.getLogger(__name__)
# Ensure logs are visible
if not logger.handlers:
    handler = logging.StreamHandler()
    handler.setLevel(logging.DEBUG)
    formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
    handler.setFormatter(formatter)
    logger.addHandler(handler)
    logger.setLevel(logging.DEBUG)

# Global progress status
enrichment_status = {
    "running": False,
    "total": 0,
    "processed": 0,
    "success": 0,
    "failed": 0,
    "last_error": None,
    "current_item": None,
    "streaming_content": "",
}

SYSTEM_PROMPT = """你是一个内容分析与分类助手。给定一篇信息流内容，请分析并推断分类，并提取或估计商品信息，以丰富客户端的 Demo 展示。请输出 JSON。

你需要完成以下任务：
1. **分类** (category)：根据内容推断其所属的大致分类（如：科技、数码、生活、游戏、资讯等）。
2. **内容类型** (item_type)：从以下选项中选择最合适的类型
   - "article": 普通文章/新闻/博客
   - "video": 视频内容（含视频链接、视频评测、视频教程等）
   - "product": 商品/产品推荐/评测/开箱（重点在某个具体产品）
   - "local": 本地生活/探店/美食/线下活动
   - "ad": 广告/赞助内容

3. **广告与商品特有字段**：
   - "brand": 如涉及品牌/公司则提取（如 "Apple", "星巴克"）。如果没有，为了 Demo 目的请合理估计一个（如 "酷比魔方"）。
   - "cta_text": 行动呼吁文本。为了 Demo 目的请合理估计一个（如 "立即抢购", "了解详情"）。
   - "price_text": 商品价格或优惠文本。如果没有，为了 Demo 目的请合理估计一个（如 "￥59起", "买一送一", "2599元"）。
   - "is_sponsored": 是否是赞助/广告内容 (Boolean: true/false)

注意：由于这是为了 Demo 展示，为了让客户端能够展示出所有种类的信息，遇到如“酷比魔方掌玩 mini 4 Pro”之类的产品、评测或新闻时，请大胆推断或直接估计出 brand、cta_text 和 price_text，不要让它们为 null。

请严格输出 JSON 格式（不要输出任何 Markdown 格式代码块，只输出纯 JSON）：
{
  "category": "数码",
  "item_type": "product",
  "brand": "酷比魔方",
  "cta_text": "立即抢购",
  "price_text": "￥2599",
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
    except json.JSONDecodeError:
        # Try to fix incomplete JSON (e.g., truncated string values)
        try:
            # If JSON is incomplete, try to close open strings and brackets
            fixed = text
            # Count open vs close quotes to detect unclosed strings
            if fixed.count('"') % 2 != 0:
                fixed += '"'
            # Try to close any open brackets
            open_braces = fixed.count('{') - fixed.count('}')
            open_brackets = fixed.count('[') - fixed.count(']')
            fixed += ']' * open_brackets
            fixed += '}' * open_braces
            return json.loads(fixed)
        except Exception:
            logger.error(f"Failed to parse LLM JSON response:\n{text}")
            return None

async def call_llm(config: LlmConfig, item: FeedItem, max_retries: int = 3) -> dict | None:
    """Call OpenAI-compatible API to analyze a single FeedItem with streaming."""
    user_prompt = f"""标题：{item.title}
来源：{item.source_name or '未知'}
分类：{item.category or '未知'}
摘要：{(item.summary or '无')[:300]}
有图片：{'是' if item.image_url else '否'}
有视频：{'是' if item.media_url else '否'}
原文链接：{item.source_url or '无'}"""

    messages = [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": user_prompt},
    ]

    url = config.base_url.rstrip("/")
    if not url.endswith("/v1"):
        url += "/v1"
    url += "/chat/completions"

    headers = {"Content-Type": "application/json"}
    if config.api_key:
        headers["Authorization"] = f"Bearer {config.api_key}"

    # Try streaming first
    for attempt in range(max_retries):
        full_content = ""
        try:
            payload = {
                "model": config.model,
                "messages": messages,
                "max_tokens": config.max_tokens,
                "temperature": config.temperature,
                "stream": True,
            }
            async with httpx.AsyncClient(timeout=config.timeout) as client:
                async with client.stream("POST", url, json=payload, headers=headers) as resp:
                    resp.raise_for_status()
                    async for line in resp.aiter_lines():
                        if line.startswith("data: "):
                            data = line[6:]
                            if data == "[DONE]":
                                break
                            try:
                                chunk = json.loads(data)
                                delta = chunk["choices"][0]["delta"]
                                if "content" in delta and delta["content"]:
                                    full_content += delta["content"]
                                    enrichment_status["streaming_content"] = full_content[-200:]
                            except (json.JSONDecodeError, KeyError, IndexError):
                                continue
                    enrichment_status["streaming_content"] = ""
                    logger.debug(f"LLM raw response for {item.id}: {full_content[:500]}")
                    return extract_json(full_content)
        except Exception as e:
            last_error = e
            logger.warning(f"Stream attempt {attempt + 1}/{max_retries} failed: {type(e).__name__}: {e}")
            if full_content:
                logger.warning(f"Partial content received before error: {full_content[-300:]}")
            if attempt < max_retries - 1:
                await asyncio.sleep(2 * (attempt + 1))
                continue

    # Fallback to non-streaming if all streaming attempts failed
    logger.info(f"Streaming failed for {item.id}, falling back to non-streaming mode")
    try:
        payload = {
            "model": config.model,
            "messages": messages,
            "max_tokens": config.max_tokens,
            "temperature": config.temperature,
            "stream": False,
        }
        async with httpx.AsyncClient(timeout=config.timeout) as client:
            resp = await client.post(url, json=payload, headers=headers)
            resp.raise_for_status()
            data = resp.json()
            content = data["choices"][0]["message"]["content"]
            logger.debug(f"LLM non-stream response for {item.id}: {content[:500]}")
            return extract_json(content)
    except Exception as e:
        logger.error(f"Non-stream fallback also failed for {item.id}: {e}")
        raise last_error

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
    
    # Update in-place so imported references see the changes
    enrichment_status.update({
        "running": True,
        "total": len(item_ids),
        "processed": 0,
        "success": 0,
        "failed": 0,
        "last_error": None,
        "current_item": None,
        "streaming_content": "",
    })

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

            # Track current item being processed
            enrichment_status["current_item"] = {
                "id": item.id,
                "title": item.title[:50] if item.title else "无标题",
            }

            try:
                result = await call_llm(config, item)
                if result:
                    # Update category and type fields if valid
                    new_item_type = result.get("item_type", item.item_type)
                    if new_item_type in ("article", "video", "product", "local", "ad"):
                        item.item_type = new_item_type
                    
                    # Local algorithm for card_type
                    if item.item_type == "video":
                        item.card_type = "video"
                    elif item.item_type == "product":
                        item.card_type = "product"
                    elif item.image_url:
                        if item.card_type not in ("large_image", "small_image"):
                            item.card_type = "large_image"
                    else:
                        item.card_type = "text_only"
                        
                    if "category" in result and result["category"]:
                        item.category = normalize_category(result["category"], default=item.category)
                    
                    # Update AI fields (tags, summary, reason are generated on client side instead)
                    item.brand = result.get("brand") or item.brand
                    item.cta_text = result.get("cta_text") or item.cta_text
                    item.price_text = result.get("price_text") or item.price_text
                    item.is_sponsored = result.get("is_sponsored", False) or item.is_sponsored
                    item.category = normalize_item_category(
                        item.category,
                        item.item_type,
                        bool(item.is_custom or item.is_sponsored),
                    )
                    
                    item.ai_enriched = True
                    db.commit()
                    enrichment_status["success"] += 1
                else:
                    raise ValueError("Failed to parse JSON response from LLM")
            except Exception as e:
                logger.error(f"LLM enrichment failed for {item_id}: {e}\n{traceback.format_exc()}")
                enrichment_status["failed"] += 1
                enrichment_status["last_error"] = f"Item {item_id}: {str(e)}"

            enrichment_status["processed"] += 1
            enrichment_status["current_item"] = None
            enrichment_status["streaming_content"] = ""
            # Add a small delay between requests to be polite to the LLM API
            await asyncio.sleep(0.5)
    finally:
        db.close()
        enrichment_status["running"] = False
        enrichment_status["current_item"] = None
        enrichment_status["streaming_content"] = ""
