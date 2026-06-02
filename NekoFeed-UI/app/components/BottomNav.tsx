'use client';

import { usePathname, useRouter } from 'next/navigation';
import { Home, Search, BarChart3, User } from 'lucide-react';

const NAV_ITEMS = [
  { href: '/', label: '首页', icon: Home },
  { href: '/search', label: '搜索', icon: Search },
  { href: '/stats', label: '统计', icon: BarChart3 },
  { href: '/profile', label: '我的', icon: User },
];

export default function BottomNav() {
  const pathname = usePathname();
  const router = useRouter();

  // Hide bottom nav on detail pages
  if (pathname.startsWith('/ad/')) return null;

  return (
    <nav className="bottom-nav">
      {NAV_ITEMS.map(item => {
        const Icon = item.icon;
        const isActive = item.href === '/' ? pathname === '/' : pathname.startsWith(item.href) && item.href !== '#';
        return (
          <button
            key={item.label}
            className={`bottom-nav-item ${isActive ? 'active' : ''}`}
            onClick={() => {
              if (item.href !== '#') router.push(item.href);
            }}
          >
            <Icon size={22} strokeWidth={isActive ? 2.5 : 2} />
            <span>{item.label}</span>
          </button>
        );
      })}
    </nav>
  );
}
