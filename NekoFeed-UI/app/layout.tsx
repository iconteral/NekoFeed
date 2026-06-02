import './globals.css';
import type { Metadata } from 'next';
import { AdProvider } from './context/AdContext';
import BottomNav from './components/BottomNav';

export const metadata: Metadata = {
  title: 'NekoFeed AI — 灵动广告流',
  description: '现代化的单列智能信息流广告推荐演示，展示 AI 摘要、智能标签与对话式搜索',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="zh">
      <body>
        <AdProvider>
          {/* 模拟移动端容器 */}
          <div style={{
            maxWidth: '480px',
            margin: '0 auto',
            minHeight: '100vh',
            backgroundColor: 'var(--color-bg)',
            position: 'relative',
            boxShadow: '0 0 60px rgba(0,0,0,0.12)',
          }}>
            {children}
            <BottomNav />
          </div>
        </AdProvider>
      </body>
    </html>
  );
}
