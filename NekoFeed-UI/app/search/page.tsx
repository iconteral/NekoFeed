'use client';

import { useState, useEffect, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { motion, AnimatePresence } from 'framer-motion';
import { ChevronLeft, Sparkles, Search, Heart, Bookmark, ArrowRight } from 'lucide-react';
import { useAdContext, AdItem } from '../context/AdContext';

const SUGGESTIONS = [
  '学生党平价耳机',
  '周末探店',
  '运动健身',
  '本地美食优惠',
  '潮流穿搭',
  '旅行攻略',
];

export default function SearchPage() {
  const router = useRouter();
  const { searchAds, toggleLike, toggleCollect } = useAdContext();
  const [query, setQuery] = useState('');
  const [isSearching, setIsSearching] = useState(false);
  const [hasSearched, setHasSearched] = useState(false);
  const [result, setResult] = useState<{ keywords: string[]; matchedTags: string[]; results: AdItem[] }>({ keywords: [], matchedTags: [], results: [] });

  const doSearch = useCallback((q: string) => {
    if (!q.trim()) return;
    setIsSearching(true);
    setHasSearched(false);
    // Simulate AI thinking delay
    setTimeout(() => {
      const res = searchAds(q);
      setResult(res);
      setIsSearching(false);
      setHasSearched(true);
    }, 1200);
  }, [searchAds]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    doSearch(query);
  };

  const handleSuggestion = (s: string) => {
    setQuery(s);
    doSearch(s);
  };

  return (
    <div style={{ minHeight: '100vh', backgroundColor: 'var(--color-bg)', paddingBottom: '40px' }}>
      {/* Header */}
      <header className="glass-panel" style={{
        position: 'sticky',
        top: 0,
        zIndex: 20,
        padding: '14px 20px',
        display: 'flex',
        alignItems: 'center',
        gap: '12px'
      }}>
        <button
          onClick={() => router.back()}
          style={{ background: 'none', border: 'none', color: 'var(--color-text-main)', cursor: 'pointer', padding: '4px' }}
        >
          <ChevronLeft size={24} />
        </button>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Sparkles size={18} color="var(--color-primary)" />
          <h1 style={{ fontSize: '1.0625rem', fontWeight: 700 }}>AI 对话搜索</h1>
        </div>
      </header>

      {/* Search Input */}
      <div style={{ padding: '20px 20px 0' }}>
        <form onSubmit={handleSubmit}>
          <div className="search-input-wrap">
            <input
              type="text"
              placeholder="描述你想看的广告内容..."
              value={query}
              onChange={e => setQuery(e.target.value)}
              autoFocus
            />
            <button type="submit" className="search-btn" disabled={isSearching}>
              <Search size={16} />
              搜索
            </button>
          </div>
        </form>
      </div>

      {/* Suggestion Chips */}
      {!hasSearched && !isSearching && (
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          style={{ padding: '20px 20px 0' }}
        >
          <p style={{ fontSize: '0.8125rem', color: 'var(--color-text-muted)', marginBottom: '12px', fontWeight: 500 }}>
            试试这些搜索：
          </p>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
            {SUGGESTIONS.map(s => (
              <button key={s} className="suggestion-chip" onClick={() => handleSuggestion(s)}>
                <Sparkles size={12} />
                {s}
              </button>
            ))}
          </div>
        </motion.div>
      )}

      {/* AI Thinking */}
      {isSearching && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          style={{ padding: '40px 20px', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px' }}
        >
          <div style={{ width: '48px', height: '48px', borderRadius: 'var(--radius-full)', background: 'var(--color-primary-soft)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Sparkles size={24} color="var(--color-primary)" />
          </div>
          <p style={{ fontSize: '0.875rem', color: 'var(--color-text-muted)', fontWeight: 500 }}>AI 正在理解你的需求...</p>
          <div className="ai-thinking-dots">
            <span /><span /><span />
          </div>
        </motion.div>
      )}

      {/* AI Understanding Result */}
      {hasSearched && (
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          style={{ padding: '20px' }}
        >
          {/* AI parsed info */}
          <div className="ai-result-card" style={{ marginBottom: '20px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
              <Sparkles size={16} color="var(--color-primary)" />
              <span style={{ fontSize: '0.8125rem', fontWeight: 600, color: 'var(--color-primary)' }}>AI 理解结果</span>
            </div>

            {result.keywords.length > 0 && (
              <div style={{ marginBottom: '10px' }}>
                <span style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)' }}>提取关键词：</span>
                <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginTop: '6px' }}>
                  {result.keywords.map(kw => (
                    <span key={kw} style={{ fontSize: '0.75rem', padding: '4px 10px', borderRadius: 'var(--radius-full)', background: 'var(--color-surface)', fontWeight: 600, color: 'var(--color-text-secondary)' }}>{kw}</span>
                  ))}
                </div>
              </div>
            )}

            {result.matchedTags.length > 0 && (
              <div>
                <span style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)' }}>匹配标签：</span>
                <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginTop: '6px' }}>
                  {result.matchedTags.map(tag => (
                    <span key={tag} className="tag-chip default">{tag}</span>
                  ))}
                </div>
              </div>
            )}
          </div>

          {/* Search Results */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '14px' }}>
            <h2 style={{ fontSize: '1rem', fontWeight: 700 }}>
              搜索结果 <span style={{ color: 'var(--color-primary)', fontSize: '0.875rem' }}>({result.results.length})</span>
            </h2>
          </div>

          {result.results.length === 0 ? (
            <div className="empty-state" style={{ padding: '40px 20px' }}>
              <Search size={40} className="icon" />
              <p style={{ fontWeight: 600, fontSize: '0.9375rem' }}>未找到匹配的广告</p>
              <p style={{ fontSize: '0.8125rem' }}>换个关键词试试吧</p>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              <AnimatePresence>
                {result.results.map((ad, idx) => (
                  <motion.div
                    key={ad.id}
                    initial={{ opacity: 0, y: 16 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: idx * 0.08 }}
                    className="glass-card"
                    style={{ cursor: 'pointer' }}
                    onClick={() => router.push(`/ad/${ad.id}`)}
                  >
                    <div style={{ display: 'flex', padding: '14px 16px', gap: '14px' }}>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <h3 style={{ fontSize: '0.9375rem', fontWeight: 700, marginBottom: '4px', lineHeight: 1.4, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{ad.title}</h3>
                        <p style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)', marginBottom: '8px', lineHeight: 1.4 }}>{ad.aiSummary}</p>
                        <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
                          {ad.aiTags.map(tag => (
                            <span key={tag} className="tag-chip default" style={{ fontSize: '0.6875rem', padding: '3px 8px' }}>{tag}</span>
                          ))}
                        </div>
                      </div>
                      <div style={{ width: '72px', height: '72px', flexShrink: 0, borderRadius: 'var(--radius-sm)', overflow: 'hidden' }}>
                        <img src={ad.imageUrl} alt={ad.title} style={{ width: '100%', height: '100%', objectFit: 'cover' }} loading="lazy" />
                      </div>
                    </div>
                  </motion.div>
                ))}
              </AnimatePresence>
            </div>
          )}
        </motion.div>
      )}
    </div>
  );
}
