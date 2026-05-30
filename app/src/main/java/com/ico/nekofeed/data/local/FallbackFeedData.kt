package com.ico.nekofeed.data.local

import com.ico.nekofeed.data.model.FeedItem

object FallbackFeedData {
    val items: List<FeedItem> = listOf(
        FeedItem(
            id = "fallback_001",
            title = "AI 技术突破：新一代大语言模型发布",
            summary = "最新一代大语言模型在多项基准测试中取得了突破性进展，展示了更强的推理能力和多模态理解能力。",
            content = "随着人工智能技术的快速发展，新一代大语言模型在自然语言处理、代码生成、多模态理解等方面都取得了显著进步。这些突破不仅推动了AI技术的发展，也为各行各业带来了新的机遇和挑战。",
            sourceName = "科技日报",
            sourceUrl = "https://example.com/tech/ai",
            category = "tech",
            itemType = "article",
            cardType = "large_image",
            imageUrl = null,
            mediaUrl = null,
            tags = listOf("AI", "技术", "大语言模型"),
            publishedAt = "2026-05-30T10:00:00"
        ),
        FeedItem(
            id = "fallback_002",
            title = "智能降噪耳机推荐",
            summary = "专为学生和通勤人群设计的高性价比降噪耳机，提供出色的音质和舒适的佩戴体验。",
            content = "这款降噪耳机采用了先进的主动降噪技术，能够有效隔绝环境噪音。同时支持蓝牙5.3连接，续航时间长达40小时。",
            sourceName = "数码评测",
            sourceUrl = "https://example.com/review/headphone",
            category = "ad",
            itemType = "ad",
            cardType = "large_image",
            imageUrl = null,
            mediaUrl = null,
            tags = listOf("耳机", "降噪", "学生党"),
            publishedAt = "2026-05-29T15:30:00"
        ),
        FeedItem(
            id = "fallback_003",
            title = "5G 智能手机市场分析",
            summary = "2026年5G智能手机市场持续增长，各品牌纷纷推出创新产品争夺市场份额。",
            content = "根据最新市场调研数据，5G智能手机在全球范围内的渗透率持续提升。各大厂商在芯片性能、摄像头技术、快充等方面不断创新，为消费者带来更多选择。",
            sourceName = "36Kr",
            sourceUrl = "https://example.com/business/5g",
            category = "tech",
            itemType = "article",
            cardType = "small_image",
            imageUrl = null,
            mediaUrl = null,
            tags = listOf("5G", "手机", "市场"),
            publishedAt = "2026-05-28T09:00:00"
        ),
        FeedItem(
            id = "fallback_004",
            title = "本地美食探店视频",
            summary = "带你探索城市中最受欢迎的隐藏美食店铺，发现地道的美味佳肴。",
            content = "本期视频将带你走访三家本地人气餐厅，品尝他们的招牌菜品，了解背后的故事。",
            sourceName = "美食频道",
            sourceUrl = "https://example.com/video/food",
            category = "local",
            itemType = "video",
            cardType = "video",
            imageUrl = null,
            mediaUrl = "https://example.com/video/food.mp4",
            tags = listOf("美食", "探店", "本地"),
            publishedAt = "2026-05-27T18:00:00"
        ),
        FeedItem(
            id = "fallback_005",
            title = "限时特惠：智能手表",
            summary = "原价 999 元，限时特价 599 元，支持健康监测和运动追踪。",
            content = "这款智能手表配备了高清AMOLED屏幕，支持心率监测、血氧检测、睡眠分析等多种健康功能。",
            sourceName = "数码商城",
            sourceUrl = "https://example.com/shop/watch",
            category = "ad",
            itemType = "product",
            cardType = "large_image",
            imageUrl = null,
            mediaUrl = null,
            tags = listOf("智能手表", "健康", "特惠"),
            publishedAt = "2026-05-26T12:00:00"
        ),
        FeedItem(
            id = "fallback_006",
            title = "机器学习入门指南",
            summary = "从零开始学习机器学习的基本概念和常用算法，适合初学者入门。",
            content = "本文将介绍机器学习的基本概念、监督学习与无监督学习的区别、常用算法如线性回归、决策树、神经网络等。",
            sourceName = "学习平台",
            sourceUrl = "https://example.com/learn/ml",
            category = "tech",
            itemType = "article",
            cardType = "large_image",
            imageUrl = null,
            mediaUrl = null,
            tags = listOf("机器学习", "教程", "入门"),
            publishedAt = "2026-05-25T14:00:00"
        )
    )
}
