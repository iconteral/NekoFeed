'use client';

import React, { createContext, useContext, useState, useCallback, ReactNode } from 'react';

/* ─── Types ─── */
export type AdChannel = '精选' | '电商' | '本地' | '视频';
export type AdCardType = 'LARGE_IMAGE' | 'SMALL_IMAGE' | 'VIDEO';

export type AdItem = {
  id: string;
  title: string;
  brand: string;
  description: string;
  type: AdCardType;
  channel: AdChannel;
  imageUrl: string;
  videoUrl?: string;
  aiSummary: string;
  aiTags: string[];
  isLiked: boolean;
  isCollected: boolean;
  likeCount: number;
  collectCount: number;
  shareCount: number;
  exposureCount: number;
  clickCount: number;
  originalDescription: string;
};

/* ─── Mock Data ─── */
const MOCK_DATA: AdItem[] = [
  {
    id: '1',
    title: '重塑听觉边界，AI降噪旗舰耳机',
    brand: 'Neko Audio',
    description: '千元级性价比降噪耳机',
    type: 'LARGE_IMAGE',
    channel: '精选',
    imageUrl: 'https://images.unsplash.com/photo-1618366712010-f4ae9c647dcb?auto=format&fit=crop&q=80&w=800',
    aiSummary: '千元级性价比首选，主打深度降噪与全天续航。',
    aiTags: ['#数码', '#性价比', '#学生党'],
    isLiked: false,
    isCollected: false,
    likeCount: 238,
    collectCount: 67,
    shareCount: 15,
    exposureCount: 1240,
    clickCount: 186,
    originalDescription: '全新一代Neko Audio旗舰降噪耳机，搭载自研AI音频芯片，精准识别环境噪音并提供高达45dB的深度降噪。30小时超长续航，支持快充，充电10分钟聆听2小时。专为学生与通勤族打造。'
  },
  {
    id: '2',
    title: '周末探店：发现隐藏在胡同里的复古咖啡馆',
    brand: 'Time Coffee',
    description: '隐藏在胡同里的网红咖啡馆',
    type: 'SMALL_IMAGE',
    channel: '本地',
    imageUrl: 'https://images.unsplash.com/photo-1509042239860-f550ce710b93?auto=format&fit=crop&q=80&w=400',
    aiSummary: '氛围感拉满的拍照圣地，适合周末放松。',
    aiTags: ['#本地生活', '#探店', '#周末去哪儿'],
    isLiked: true,
    isCollected: false,
    likeCount: 542,
    collectCount: 189,
    shareCount: 76,
    exposureCount: 2100,
    clickCount: 340,
    originalDescription: '这家藏在深巷里的Time Coffee，完整保留了上世纪的红砖与木质横梁。手冲瑰夏带有明亮的柑橘风味，配上招牌的海盐焦糖蛋糕，绝对是周末下午茶的完美组合。'
  },
  {
    id: '3',
    title: '第一视角：穿越峡谷的纯电越野体验',
    brand: 'Volt Motors',
    description: '纯电越野SUV峡谷穿越体验',
    type: 'VIDEO',
    channel: '视频',
    imageUrl: 'https://images.unsplash.com/photo-1563720223185-11003d516935?auto=format&fit=crop&q=80&w=800',
    videoUrl: 'https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4',
    aiSummary: '无惧地形挑战，展现极致电动越野性能。',
    aiTags: ['#汽车', '#越野', '#纯电'],
    isLiked: false,
    isCollected: true,
    likeCount: 1023,
    collectCount: 456,
    shareCount: 210,
    exposureCount: 5600,
    clickCount: 890,
    originalDescription: 'Volt Motors全新纯电越野SUV，双电机四驱爆发出超强扭矩。独创的智能悬挂系统让峡谷穿越如履平地。跟随我们的镜头，一起感受肾上腺素飙升的极致驾驶乐趣。'
  },
  {
    id: '4',
    title: '春季限定：樱花粉联名运动鞋',
    brand: 'StepX',
    description: '春季限定联名款运动鞋',
    type: 'LARGE_IMAGE',
    channel: '电商',
    imageUrl: 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&q=80&w=800',
    aiSummary: '限量联名配色，兼顾颜值与缓震性能。',
    aiTags: ['#运动', '#限定', '#潮流'],
    isLiked: false,
    isCollected: false,
    likeCount: 876,
    collectCount: 321,
    shareCount: 98,
    exposureCount: 3400,
    clickCount: 520,
    originalDescription: 'StepX 与知名设计师联名打造的春季限定樱花粉系列。采用全新FlexFoam缓震科技，鞋面使用再生编织材料，既环保又透气。每一步都是时尚与科技的完美融合。'
  },
  {
    id: '5',
    title: '智能美妆镜：AI肤质检测，精准护肤推荐',
    brand: 'GlowTech',
    description: 'AI智能美妆镜',
    type: 'SMALL_IMAGE',
    channel: '电商',
    imageUrl: 'https://images.unsplash.com/photo-1596462502278-27bfdc403348?auto=format&fit=crop&q=80&w=400',
    aiSummary: '内置AI肤质分析，实时推荐护肤方案。',
    aiTags: ['#美妆', '#智能家居', '#护肤'],
    isLiked: false,
    isCollected: false,
    likeCount: 156,
    collectCount: 89,
    shareCount: 23,
    exposureCount: 890,
    clickCount: 120,
    originalDescription: 'GlowTech 智能美妆镜搭载百万像素高清摄像头与AI肤质分析引擎。实时检测肌肤含水量、油脂分泌和毛孔状态，并根据当日天气与环境给出个性化护肤建议。'
  },
  {
    id: '6',
    title: '城市骑行计划：48小时环城记录',
    brand: 'RideNow',
    description: '城市骑行环城活动',
    type: 'VIDEO',
    channel: '视频',
    imageUrl: 'https://images.unsplash.com/photo-1571068316344-75bc76f77890?auto=format&fit=crop&q=80&w=800',
    videoUrl: 'https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4',
    aiSummary: '记录48小时城市骑行冒险，感受城市脉搏。',
    aiTags: ['#运动', '#骑行', '#城市生活'],
    isLiked: true,
    isCollected: false,
    likeCount: 672,
    collectCount: 234,
    shareCount: 145,
    exposureCount: 2800,
    clickCount: 410,
    originalDescription: '跟随 RideNow 骑行俱乐部，用48小时丈量这座城市。穿越老城区的石板路、沿江骑行道、工业遗址公园……这是一场关于速度、自由和发现的旅程。'
  },
  {
    id: '7',
    title: '零食盲盒：来自世界各地的惊喜',
    brand: 'Snack Globe',
    description: '全球零食盲盒',
    type: 'SMALL_IMAGE',
    channel: '电商',
    imageUrl: 'https://images.unsplash.com/photo-1621939514649-280e2ee25f60?auto=format&fit=crop&q=80&w=400',
    aiSummary: '每月精选全球特色零食，打开即惊喜。',
    aiTags: ['#零食', '#盲盒', '#新奇体验'],
    isLiked: false,
    isCollected: false,
    likeCount: 445,
    collectCount: 198,
    shareCount: 87,
    exposureCount: 1560,
    clickCount: 267,
    originalDescription: 'Snack Globe每月从全球20多个国家精选10-15款特色零食。日本抹茶巧克力、韩国蜂蜜薯片、墨西哥辣味糖果……每一箱都是一场味蕾的环球旅行。'
  },
  {
    id: '8',
    title: '深夜食堂：探访24小时营业的居酒屋',
    brand: '味道集市',
    description: '24小时居酒屋探店',
    type: 'LARGE_IMAGE',
    channel: '本地',
    imageUrl: 'https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&q=80&w=800',
    aiSummary: '深夜觅食好去处，日式居酒屋温暖治愈。',
    aiTags: ['#美食', '#深夜食堂', '#本地生活'],
    isLiked: false,
    isCollected: false,
    likeCount: 312,
    collectCount: 143,
    shareCount: 56,
    exposureCount: 1820,
    clickCount: 298,
    originalDescription: '在这座不夜城的角落，有一家24小时营业的居酒屋。烟火气十足的烤串、热腾腾的味噌汤、冰爽的生啤——这里是夜归人最温暖的栖息地。'
  },
  {
    id: '9',
    title: '极简主义：轻量化通勤背包',
    brand: 'MinimalPack',
    description: '极简通勤背包',
    type: 'SMALL_IMAGE',
    channel: '电商',
    imageUrl: 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&q=80&w=400',
    aiSummary: '仅重380g的极简通勤背包，学生党通勤神器。',
    aiTags: ['#通勤', '#学生党', '#极简'],
    isLiked: false,
    isCollected: false,
    likeCount: 198,
    collectCount: 76,
    shareCount: 34,
    exposureCount: 920,
    clickCount: 134,
    originalDescription: 'MinimalPack 将轻量化设计发挥到极致。整包仅重380g，却拥有15L容量和防泼水面料。磁吸快开扣、隐藏式笔记本内袋、可拆卸腰带——每一个细节都经过精心打磨。'
  },
  {
    id: '10',
    title: '周末亲子：森林公园自然探索课',
    brand: '自然学院',
    description: '亲子自然探索活动',
    type: 'LARGE_IMAGE',
    channel: '本地',
    imageUrl: 'https://images.unsplash.com/photo-1441974231531-c6227db76b6e?auto=format&fit=crop&q=80&w=800',
    aiSummary: '让孩子亲近自然，周末亲子户外教育好选择。',
    aiTags: ['#亲子', '#户外', '#教育'],
    isLiked: false,
    isCollected: true,
    likeCount: 234,
    collectCount: 167,
    shareCount: 112,
    exposureCount: 1340,
    clickCount: 209,
    originalDescription: '自然学院推出的周末亲子探索课，在城市近郊的森林公园内开设。专业自然导师带领孩子观察昆虫、辨识植物、制作标本。让孩子在玩耍中学习，在自然中成长。'
  },
  {
    id: '11',
    title: '30天产品设计大师课：从零到作品集',
    brand: 'DesignPro',
    description: '产品设计在线课程',
    type: 'SMALL_IMAGE',
    channel: '精选',
    imageUrl: 'https://images.unsplash.com/photo-1586717791821-3f44a563fa4c?auto=format&fit=crop&q=80&w=400',
    aiSummary: '系统化设计课程，30天搭建完整作品集。',
    aiTags: ['#教育', '#设计', '#技能提升'],
    isLiked: false,
    isCollected: false,
    likeCount: 567,
    collectCount: 234,
    shareCount: 89,
    exposureCount: 2340,
    clickCount: 389,
    originalDescription: 'DesignPro 联合顶尖设计师推出的30天产品设计大师课。从设计基础理论到Figma实战，从用户研究到交互原型，每天2小时，30天后拥有一份完整的作品集。'
  },
  {
    id: '12',
    title: '健身新玩法：AI私教实时纠正动作',
    brand: 'FitAI',
    description: 'AI健身私教应用',
    type: 'VIDEO',
    channel: '视频',
    imageUrl: 'https://images.unsplash.com/photo-1534438327276-14e5300c3a48?auto=format&fit=crop&q=80&w=800',
    videoUrl: 'https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4',
    aiSummary: 'AI识别运动姿态，实时提供动作矫正建议。',
    aiTags: ['#健身', '#AI', '#运动'],
    isLiked: false,
    isCollected: false,
    likeCount: 789,
    collectCount: 345,
    shareCount: 156,
    exposureCount: 3200,
    clickCount: 567,
    originalDescription: 'FitAI 利用先进的姿态识别技术，通过手机摄像头实时分析你的运动姿势。当检测到不标准动作时，AI教练会即时提供语音纠正建议，有效避免运动损伤。'
  },
  {
    id: '13',
    title: '小众香水：调出属于你的独特气息',
    brand: 'Scent Lab',
    description: '定制香水体验',
    type: 'LARGE_IMAGE',
    channel: '精选',
    imageUrl: 'https://images.unsplash.com/photo-1541643600914-78b084683601?auto=format&fit=crop&q=80&w=800',
    aiSummary: '个性化调香体验，打造专属你的气息密码。',
    aiTags: ['#香水', '#个性化', '#生活方式'],
    isLiked: false,
    isCollected: false,
    likeCount: 432,
    collectCount: 198,
    shareCount: 67,
    exposureCount: 1670,
    clickCount: 256,
    originalDescription: 'Scent Lab 提供完全个性化的调香体验。专业调香师根据你的性格测试结果和香味偏好，从100+种天然香料中为你调配独一无二的专属香水。每一瓶都是限量版的你。'
  },
  {
    id: '14',
    title: '社区团购：今日精选应季水果',
    brand: '鲜果日记',
    description: '社区团购应季水果',
    type: 'SMALL_IMAGE',
    channel: '本地',
    imageUrl: 'https://images.unsplash.com/photo-1619566636858-adf3ef46400b?auto=format&fit=crop&q=80&w=400',
    aiSummary: '产地直供新鲜水果，社区自提更方便。',
    aiTags: ['#生鲜', '#本地生活', '#优惠'],
    isLiked: false,
    isCollected: false,
    likeCount: 123,
    collectCount: 45,
    shareCount: 34,
    exposureCount: 670,
    clickCount: 89,
    originalDescription: '鲜果日记与全国优质产地直接合作，当日采摘次日送达。本周精选：云南蓝莓、海南芒果、烟台樱桃。社区自提点取货，新鲜看得见。'
  },
  {
    id: '15',
    title: '旅行Vlog：72小时穷游东南亚攻略',
    brand: '行者日记',
    description: '东南亚穷游攻略视频',
    type: 'VIDEO',
    channel: '视频',
    imageUrl: 'https://images.unsplash.com/photo-1552733407-5d5c46c3bb3b?auto=format&fit=crop&q=80&w=800',
    videoUrl: 'https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4',
    aiSummary: '3天2夜东南亚穷游攻略，人均不到2000。',
    aiTags: ['#旅行', '#穷游', '#攻略'],
    isLiked: false,
    isCollected: false,
    likeCount: 1567,
    collectCount: 876,
    shareCount: 432,
    exposureCount: 6700,
    clickCount: 1230,
    originalDescription: '行者日记带你用最少的预算玩转东南亚！72小时内，我们穿越了曼谷的夜市、清迈的古寺、芭提雅的海滩。住青旅、吃路边摊、坐公共交通——全程人均花费不到2000元。'
  }
];

/* ─── Stats type ─── */
export type StatsData = {
  totalExposure: number;
  totalClicks: number;
  totalLikes: number;
  totalCollects: number;
  totalShares: number;
  ctr: number;
  ranking: AdItem[];
};

/* ─── Context type ─── */
type AdContextType = {
  ads: AdItem[];
  toggleLike: (id: string) => void;
  toggleCollect: (id: string) => void;
  incrementExposure: (id: string) => void;
  incrementClick: (id: string) => void;
  incrementShare: (id: string) => void;
  getAdById: (id: string) => AdItem | undefined;
  getAdsByChannel: (channel: AdChannel) => AdItem[];
  searchAds: (query: string) => { keywords: string[]; matchedTags: string[]; results: AdItem[] };
  getStats: () => StatsData;
};

const AdContext = createContext<AdContextType | undefined>(undefined);

export function AdProvider({ children }: { children: ReactNode }) {
  const [ads, setAds] = useState<AdItem[]>(MOCK_DATA);

  const toggleLike = useCallback((id: string) => {
    setAds(prev => prev.map(ad =>
      ad.id === id ? { ...ad, isLiked: !ad.isLiked, likeCount: ad.isLiked ? ad.likeCount - 1 : ad.likeCount + 1 } : ad
    ));
  }, []);

  const toggleCollect = useCallback((id: string) => {
    setAds(prev => prev.map(ad =>
      ad.id === id ? { ...ad, isCollected: !ad.isCollected, collectCount: ad.isCollected ? ad.collectCount - 1 : ad.collectCount + 1 } : ad
    ));
  }, []);

  const incrementExposure = useCallback((id: string) => {
    setAds(prev => prev.map(ad =>
      ad.id === id ? { ...ad, exposureCount: ad.exposureCount + 1 } : ad
    ));
  }, []);

  const incrementClick = useCallback((id: string) => {
    setAds(prev => prev.map(ad =>
      ad.id === id ? { ...ad, clickCount: ad.clickCount + 1 } : ad
    ));
  }, []);

  const incrementShare = useCallback((id: string) => {
    setAds(prev => prev.map(ad =>
      ad.id === id ? { ...ad, shareCount: ad.shareCount + 1 } : ad
    ));
  }, []);

  const getAdById = useCallback((id: string) => {
    return ads.find(ad => ad.id === id);
  }, [ads]);

  const getAdsByChannel = useCallback((channel: AdChannel) => {
    if (channel === '精选') return ads;
    return ads.filter(ad => ad.channel === channel || ad.channel === '精选');
  }, [ads]);

  const searchAds = useCallback((query: string) => {
    const q = query.toLowerCase().trim();
    if (!q) return { keywords: [], matchedTags: [], results: [] };

    // Extract keywords (split by spaces, commas, etc.)
    const keywords = q.split(/[\s,，、]+/).filter(Boolean);

    // Find matching tags from all ads
    const allTags = new Set<string>();
    ads.forEach(ad => ad.aiTags.forEach(t => allTags.add(t)));
    const matchedTags = Array.from(allTags).filter(tag =>
      keywords.some(kw => tag.toLowerCase().includes(kw))
    );

    // Score and filter ads
    const scored = ads.map(ad => {
      let score = 0;
      const searchable = `${ad.title} ${ad.aiSummary} ${ad.originalDescription} ${ad.brand} ${ad.aiTags.join(' ')}`.toLowerCase();
      keywords.forEach(kw => {
        if (ad.title.toLowerCase().includes(kw)) score += 3;
        if (ad.aiSummary.toLowerCase().includes(kw)) score += 2;
        if (ad.aiTags.some(t => t.toLowerCase().includes(kw))) score += 2;
        if (ad.originalDescription.toLowerCase().includes(kw)) score += 1;
        if (ad.brand.toLowerCase().includes(kw)) score += 1;
      });
      return { ad, score };
    }).filter(s => s.score > 0).sort((a, b) => b.score - a.score);

    return { keywords, matchedTags, results: scored.map(s => s.ad) };
  }, [ads]);

  const getStats = useCallback((): StatsData => {
    const totalExposure = ads.reduce((sum, ad) => sum + ad.exposureCount, 0);
    const totalClicks = ads.reduce((sum, ad) => sum + ad.clickCount, 0);
    const totalLikes = ads.reduce((sum, ad) => sum + ad.likeCount, 0);
    const totalCollects = ads.reduce((sum, ad) => sum + ad.collectCount, 0);
    const totalShares = ads.reduce((sum, ad) => sum + ad.shareCount, 0);
    const ctr = totalExposure > 0 ? (totalClicks / totalExposure) * 100 : 0;
    const ranking = [...ads].sort((a, b) => b.exposureCount - a.exposureCount);
    return { totalExposure, totalClicks, totalLikes, totalCollects, totalShares, ctr, ranking };
  }, [ads]);

  return (
    <AdContext.Provider value={{
      ads, toggleLike, toggleCollect, incrementExposure, incrementClick, incrementShare,
      getAdById, getAdsByChannel, searchAds, getStats
    }}>
      {children}
    </AdContext.Provider>
  );
}

export function useAdContext() {
  const context = useContext(AdContext);
  if (context === undefined) {
    throw new Error('useAdContext must be used within an AdProvider');
  }
  return context;
}
