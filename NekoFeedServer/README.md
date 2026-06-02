# Local Feed Server

This is a local Feed Aggregation Server designed for the "AI Advertising Recommendation Feed App" Android boot camp project. It provides a simple, stable local data source by fetching upstream RSS/Atom feeds, parsing them, downloading images/videos locally, and exposing a unified JSON API for the Android client.

It also supports custom ad items to simulate an advertising feed.

## Prerequisites

- Python 3.10+
- `pip`

## Setup & Run

1. Navigate to the `feed_server` directory:
   ```bash
   cd feed_server
   ```

2. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```

3. Initialize the database with default feeds and custom ads:
   ```bash
   python seed.py
   ```

4. Start the server:
   ```bash
   uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
   ```

## Admin Dashboard

Open your browser and navigate to:
[http://localhost:8000/admin](http://localhost:8000/admin)

From the dashboard you can:
- View and manage Upstream Feeds.
- **Refresh Feeds:** Click "Refresh All Now" or refresh individual feeds to pull the latest articles. 
- View parsed Feed Items and delete them.
- Add Custom Ad Items.

## Android Client Usage

### 1. Fetching the Feed

The Android app should make a `GET` request to `/api/feed`.

If testing on an **Android Emulator**, use `10.0.2.2` instead of `localhost` and pass the `base_url` parameter so the server constructs correct image/media URLs for the emulator.

**Request:**
```http
GET http://10.0.2.2:8000/api/feed?limit=20&offset=0&base_url=http://10.0.2.2:8000
```

**Query Parameters:**
- `category` (optional): e.g., `tech`, `ad`
- `item_type` (optional): e.g., `article`, `ad`, `video`
- `limit` (default: 20)
- `offset` (default: 0)
- `base_url` (optional): Very important for emulator testing. Sets the prefix for downloaded media paths.

**Response Example:**
```json
{
  "items": [
    {
      "id": "item_123abc...",
      "title": "Example Tech Article",
      "summary": "This is a summary...",
      "content": "Full HTML content here...",
         "sourceName": "36Kr",
         "sourceUrl": "https://36kr.com/p/123",
      "category": "tech",
         "itemType": "article",
         "cardType": "large_image",
         "imageUrl": "http://10.0.2.2:8000/media/images/hash.jpg",
         "mediaUrl": null,
      "tags": ["tech"],
         "publishedAt": "2026-05-29T10:00:00"
    }
  ],
  "limit": 20,
  "offset": 0,
  "total": 150
}
```

### 2. Media Caching
All images found in the RSS feeds are automatically downloaded to `data/media/images/` and served statically via `/media/images/`.

### 3. RSS Export
To view the aggregated content as a standard RSS feed (for testing with other readers):
[http://localhost:8000/api/rss.xml](http://localhost:8000/api/rss.xml)

## API Documentation (中文)

**Base URL**: `http://localhost:8000`

### GET /api/feed
获取聚合后的 Feed 列表。

**Query Parameters:**
- `category` (optional): 例如 `tech`, `ad`, `local`
- `item_type` (optional): 例如 `article`, `ad`, `video`, `product`
- `limit` (default: 20, 1-100)
- `offset` (default: 0)
- `base_url` (optional): 用于拼接本地缓存图片/视频的完整 URL（模拟器建议传 `http://10.0.2.2:8000`）

**Response Body:**
```json
{
   "items": [
      {
         "id": "item_123abc...",
         "title": "Example Tech Article",
         "summary": "This is a summary...",
         "content": "Full HTML content here...",
         "sourceName": "36Kr",
         "sourceUrl": "https://36kr.com/p/123",
         "category": "tech",
         "itemType": "article",
         "cardType": "large_image",
         "imageUrl": "http://10.0.2.2:8000/media/images/hash.jpg",
         "mediaUrl": null,
         "tags": ["tech"],
         "publishedAt": "2026-05-29T10:00:00"
      }
   ],
   "limit": 20,
   "offset": 0,
   "total": 150
}
```

**字段说明:**
- `itemType`: `article` | `ad` | `video` | `product`
- `cardType`: `large_image` | `small_image` | `video`
- `imageUrl`: 图片封面（可为空）
- `mediaUrl`: 视频/媒体地址（视频条目常用）
- `publishedAt`: ISO 8601 时间字符串

**视频条目示例要点:**
- `itemType = "video"`
- `cardType = "video"`
- `mediaUrl` 为视频地址，`imageUrl` 可作为封面图

### GET /api/items/{id}
获取单条 Feed 原始记录（用于调试/管理）。

**Path Parameters:**
- `id`: Feed Item 的唯一标识

**Response:**
- 返回该条目的 JSON；字段命名为数据库原始 snake_case。

### POST /api/refresh
触发所有启用的上游 Feed 刷新（异步）。

**Response:**
```json
{ "message": "Started refresh for 3 feeds" }
```

### POST /api/feeds/{id}/refresh
触发单个 Feed 刷新（异步）。

**Path Parameters:**
- `id`: Upstream Feed 的 ID

**Response:**
```json
{ "message": "Started refresh for feed: Feed Name" }
```

### GET /api/rss.xml
输出聚合后的 RSS 2.0 XML。

### Static Media
- 图片: `/media/images/...`
- 视频: `/media/videos/...`