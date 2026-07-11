import { useEffect, useMemo, useState } from 'react'
import axios from 'axios'
import WelcomeScreen from './WelcomeScreen'
import ContentManager from './components/ContentManager'
import ClipAndLearn from './components/ClipAndLearn'
import CharacterChat from './components/CharacterChat'
import PronunciationCoach from './components/PronunciationCoach'
import PersonalizedLearning from './components/PersonalizedLearning'
import RecommendedChannels from './components/RecommendedChannels'
import { AUTH_URL } from './api'
import ktalkLogo from './assets/ktalk-logo.png'
import './App.css'

const TABS = [
  { id: 'contents', label: 'AI 콘텐츠', short: '콘텐츠' },
  { id: 'clip', label: '유튜브 클립 학습', short: '클립' },
  { id: 'chat', label: 'AI 회화 연습', short: '회화' },
  { id: 'pronunciation', label: 'AI 발음 코치', short: '발음' },
  { id: 'personalized', label: '개인화 복습', short: '복습' },
]

const missionCards = [
  { title: '오늘의 미션', value: '12개', copy: '오늘 복습할 표현', tone: 'mint' },
  { title: '실전복습', value: '3분', copy: 'AI와 바로 말하기', tone: 'blue' },
  { title: '오답노트', value: '7개', copy: '다시 볼 표현', tone: 'rose' },
  { title: 'AI 발음 코치', value: '92점', copy: '최근 발음 정확도', tone: 'violet' },
  { title: '추천 유튜브 학습', value: '5개', copy: '내 수준 맞춤 클립', tone: 'amber' },
]

const featureCards = [
  {
    title: '유튜브 클립 학습',
    copy: '좋아하는 영상에서 표현을 뽑아 짧은 미션으로 학습합니다.',
  },
  {
    title: 'AI 회화 연습',
    copy: '오늘 배운 표현을 실제 대화처럼 말하며 복습합니다.',
  },
  {
    title: '발음 코치',
    copy: '내 발음을 듣고 자연스러운 억양과 정확도를 피드백합니다.',
  },
  {
    title: '오답노트',
    copy: '틀린 표현을 자동으로 모아 다음 복습 루프로 보냅니다.',
  },
  {
    title: '개인화 추천',
    copy: '레벨, 관심사, 학습 기록에 맞춰 다음 콘텐츠를 추천합니다.',
  },
]

function App() {
  const [user, setUser] = useState(null)
  const [authChecked, setAuthChecked] = useState(false)
  const [entered, setEntered] = useState(false)
  const [activeTab, setActiveTab] = useState('contents')
  const [showPasswordForm, setShowPasswordForm] = useState(false)
  const [passwordForm, setPasswordForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' })
  const [passwordChanging, setPasswordChanging] = useState(false)

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const tokenFromRedirect = params.get('token')

    if (window.location.pathname === '/oauth2/redirect' && tokenFromRedirect) {
      localStorage.setItem('token', tokenFromRedirect)
      window.history.replaceState({}, '', '/')
    }

    const tabParam = params.get('tab')
    if (tabParam && TABS.some((tab) => tab.id === tabParam)) {
      setActiveTab(tabParam)
    }

    const token = localStorage.getItem('token')
    if (!token) {
      setAuthChecked(true)
      return
    }

    axios.get(`${AUTH_URL}/me`, {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((response) => {
        if (response.data.success) {
          setUser(response.data.user)
        }
      })
      .catch(() => {
        localStorage.removeItem('token')
      })
      .finally(() => setAuthChecked(true))
  }, [])

  // "무료로 시작하기"를 누르면 실제 회원가입/로그인 없이 바로 본 화면으로
  // 들어간다(entered) — 별도의 회원가입/로그인 폼 화면 자체를 없애기로 했다.
  // 실제 계정으로 로그인된 세션(token이 유효해 user가 채워진 경우)이 있으면
  // 그것도 그대로 본 화면으로 들어간다.
  const isLoggedIn = authChecked && (!!user || entered)

  const stats = useMemo(() => [
    { label: '스트릭', value: '3일', icon: '🔥' },
    { label: 'EXP', value: '1,240', icon: '⚡' },
    { label: '레벨', value: 'Lv. 8', icon: '🏅' },
    { label: '오늘 복습', value: '12개', icon: '🎯' },
  ], [])

  const handleLogout = () => {
    localStorage.removeItem('token')
    setUser(null)
    setEntered(false)
    setShowPasswordForm(false)
  }

  const handleChangePassword = async (event) => {
    event.preventDefault()
    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      alert('새 비밀번호가 일치하지 않습니다.')
      return
    }

    setPasswordChanging(true)
    try {
      const token = localStorage.getItem('token')
      const response = await axios.post(
        `${AUTH_URL}/change-password`,
        { currentPassword: passwordForm.currentPassword, newPassword: passwordForm.newPassword },
        { headers: { Authorization: `Bearer ${token}` } },
      )
      if (response.data.success) {
        alert('비밀번호가 변경되었습니다.')
        setShowPasswordForm(false)
        setPasswordForm({ currentPassword: '', newPassword: '', confirmPassword: '' })
      }
    } catch (error) {
      alert('비밀번호 변경 실패: ' + (error.response?.data?.message || error.message))
    } finally {
      setPasswordChanging(false)
    }
  }

  const jumpToExperience = (tabId = 'contents') => {
    setActiveTab(tabId)
    document.getElementById('ai-experience')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  // 인증 확인이 끝나기 전에는 웰컴 화면도, 기존 화면도 아닌 빈 배경만
  // 보여준다 — 안 그러면 이미 로그인된 사용자에게도 웰컴 화면이 잠깐
  // 번쩍이고 사라지는 깜빡임이 생긴다.
  if (!authChecked) {
    return <div className="welcome-shell" />
  }

  // 로그인 전 방문자는 전체 기능 화면 대신 이 웰컴 화면만 본다.
  // "무료로 시작하기"를 누르면 별도 화면 전환 없이 바로 본 화면으로 들어간다.
  if (!isLoggedIn) {
    return <WelcomeScreen onStart={() => setEntered(true)} />
  }

  return (
    <div className="ktalk-shell">
      <header className="site-header">
        <a className="brand" href="#top" aria-label="K-Talk AI 홈">
          <img src={ktalkLogo} alt="" className="brand-logo" />
          <span>
            <strong>K-Talk AI</strong>
            <small>Learn with clips and conversation</small>
          </span>
        </a>

        <nav className="header-nav" aria-label="주요 메뉴">
          <a href="#features">기능</a>
          <a href="#ai-experience">복습</a>
        </nav>

        <div className="header-actions">
          {user ? (
            <div className="user-chip">
              <span>{user.username}</span>
              <button type="button" onClick={() => setShowPasswordForm((value) => !value)}>설정</button>
              <button type="button" onClick={handleLogout}>로그아웃</button>
            </div>
          ) : (
            <button className="login-button" type="button" onClick={handleLogout}>나가기</button>
          )}
        </div>
      </header>

      {showPasswordForm && user && (
        <form className="password-panel glass-card" onSubmit={handleChangePassword}>
          <input
            type="password"
            placeholder="현재 비밀번호"
            value={passwordForm.currentPassword}
            onChange={(event) => setPasswordForm({ ...passwordForm, currentPassword: event.target.value })}
            required
          />
          <input
            type="password"
            placeholder="새 비밀번호"
            value={passwordForm.newPassword}
            onChange={(event) => setPasswordForm({ ...passwordForm, newPassword: event.target.value })}
            minLength={8}
            required
          />
          <input
            type="password"
            placeholder="새 비밀번호 확인"
            value={passwordForm.confirmPassword}
            onChange={(event) => setPasswordForm({ ...passwordForm, confirmPassword: event.target.value })}
            required
          />
          <button type="submit" disabled={passwordChanging}>
            {passwordChanging ? '변경 중' : '변경'}
          </button>
        </form>
      )}

      <main id="top">
        <section className="hero-section">
          <div className="hero-copy">
            <div className="eyebrow">AI 영어 루틴 메이트</div>
            <h1>K-pop, K-drama로 배우고, AI와 말하며 복습하세요</h1>
            <p className="hero-subtitle">
              오늘 배운 표현을 실전 대화로 바꾸는 AI 영어 학습 앱
            </p>
            <div className="hero-actions">
              <button className="primary-cta" type="button" onClick={() => jumpToExperience('contents')}>
                오늘 학습 시작하기
              </button>
              <button className="secondary-cta" type="button" onClick={() => jumpToExperience('chat')}>
                AI 회화 체험하기
              </button>
            </div>
            <div className="trust-row" aria-label="학습 지표">
              {stats.map((stat) => (
                <div className="mini-stat" key={stat.label}>
                  <span>{stat.icon}</span>
                  <strong>{stat.value}</strong>
                  <small>{stat.label}</small>
                </div>
              ))}
            </div>
          </div>

          <aside className="dashboard-preview glass-card" aria-label="학습 대시보드 미리보기">
            <div className="preview-top">
              <div>
                <span className="preview-kicker">오늘의 대시보드</span>
                <h2>3일 연속 학습</h2>
              </div>
              <span className="streak-badge">🔥</span>
            </div>
            <div className="progress-ring" aria-label="진행률 68%">
              <span>68%</span>
            </div>
            <div className="preview-grid">
              <div>
                <small>오늘 복습</small>
                <strong>12개</strong>
              </div>
              <div>
                <small>획득 EXP</small>
                <strong>+180</strong>
              </div>
            </div>
            <button type="button" className="start-review" onClick={() => jumpToExperience('contents')}>
              실전복습 시작
            </button>
          </aside>
        </section>

        <section className="mission-strip" aria-label="오늘의 학습 카드">
          {missionCards.map((card) => (
            <article className={`mission-card ${card.tone}`} key={card.title}>
              <span>{card.title}</span>
              <strong>{card.value}</strong>
              <small>{card.copy}</small>
            </article>
          ))}
        </section>

        <section className="section-block" id="features">
          <div className="section-heading">
            <span className="eyebrow">Feature Cards</span>
            <h2>말하고, 틀리고, 다시 익히는 학습 루프</h2>
          </div>
          <div className="feature-grid">
            {featureCards.map((feature) => (
              <article className="feature-card glass-card" key={feature.title}>
                <h3>{feature.title}</h3>
                <p>{feature.copy}</p>
              </article>
            ))}
          </div>
        </section>

        <section className="loop-section glass-card">
          <div>
            <span className="eyebrow">Review Loop</span>
            <h2>학습 → AI 대화 → 오답 저장 → 복습 → EXP 보상</h2>
          </div>
          <div className="loop-steps">
            {['학습', 'AI 대화', '오답 저장', '복습', 'EXP 보상'].map((step) => (
              <span key={step}>{step}</span>
            ))}
          </div>
        </section>

        <section className="section-block learning-board" id="ai-experience">
          <div className="section-heading">
            <span className="eyebrow">AI Learning Lab</span>
            <h2>오늘도 이어서 학습해보세요</h2>
            <p>콘텐츠 생성, 유튜브 학습, 회화, 발음 코치까지 모든 기능을 여기서 바로 사용할 수 있습니다.</p>
          </div>

          <RecommendedChannels />

          <nav className="app-tabs" aria-label="AI 학습 기능">
            {TABS.map((tab) => (
              <button
                key={tab.id}
                type="button"
                className={activeTab === tab.id ? 'active' : ''}
                onClick={() => setActiveTab(tab.id)}
              >
                <span>{tab.label}</span>
                <small>{tab.short}</small>
              </button>
            ))}
          </nav>

          <div className="tool-surface glass-card">
            {activeTab === 'contents' && <ContentManager />}
            {activeTab === 'clip' && <ClipAndLearn />}
            {activeTab === 'chat' && <CharacterChat />}
            {activeTab === 'pronunciation' && <PronunciationCoach />}
            {activeTab === 'personalized' && <PersonalizedLearning onNavigate={setActiveTab} />}
          </div>
        </section>

        <section className="pricing-section" id="pricing">
          <div className="section-heading pricing-heading">
            <span className="eyebrow">Pricing</span>
            <h2>AI 학습량에 맞춰 선택하세요</h2>
            <p>무료로 시작하고, 매일 말하기 루틴이 생기면 Pro로 확장하세요. Pro는 월 $9.90, Business는 월 $19.90입니다.</p>
          </div>

          <div className="pricing-switch" aria-label="요금 안내">
            <span>Monthly</span>
            <strong>14-day free trial</strong>
            <span>Cancel anytime</span>
          </div>

          <div className="pricing-grid">
            <article className="pricing-card">
              <div className="pricing-card-top">
                <span className="plan-badge">Starter</span>
                <h3>Free</h3>
                <div className="price-line">
                  <strong>$0</strong>
                  <small>/month</small>
                </div>
                <p>처음 둘러보고 기본 학습 루틴을 체험하는 플랜</p>
              </div>
              <ul className="plan-features">
                <li>AI 콘텐츠 생성 월 30회</li>
                <li>오늘의 표현과 기본 복습</li>
                <li>K-pop/K-drama 클립 학습 체험</li>
              </ul>
              <button type="button" className="secondary-cta plan-button" onClick={() => jumpToExperience('contents')}>
                현재 기능 사용
              </button>
            </article>

            <article className="pricing-card recommended">
              <div className="pricing-card-top">
                <span className="plan-badge">Recommended</span>
                <h3>Pro</h3>
                <div className="price-line">
                  <strong>$9.90</strong>
                  <small>/month</small>
                </div>
                <p>매일 AI와 말하고 발음/오답 복습까지 이어가는 개인 학습자용</p>
              </div>
              <ul className="plan-features">
                <li>AI 회화와 콘텐츠 생성 월 1,000회</li>
                <li>AI 역할극과 실시간 피드백</li>
                <li>발음 코치, 오답노트, 개인화 복습</li>
                <li>유튜브 클립 기반 표현 추출</li>
              </ul>
              <button
                type="button"
                className="primary-cta plan-button"
                onClick={() => jumpToExperience('chat')}
              >
                Pro 미리보기
              </button>
            </article>

            <article className="pricing-card">
              <div className="pricing-card-top">
                <span className="plan-badge">High volume</span>
                <h3>Business</h3>
                <div className="price-line">
                  <strong>$19.90</strong>
                  <small>/month</small>
                </div>
                <p>수업 자료와 대량 복습 세트가 필요한 강사, 팀, 고급 학습자용</p>
              </div>
              <ul className="plan-features">
                <li>AI 회화와 콘텐츠 생성 월 5,000회</li>
                <li>수업용 콘텐츠 빠른 생성</li>
                <li>학습자별 복습 세트 구성</li>
                <li>팀 결제와 영수증 관리</li>
              </ul>
              <button
                type="button"
                className="secondary-cta plan-button"
                onClick={() => jumpToExperience('contents')}
              >
                Business 미리보기
              </button>
            </article>
          </div>
        </section>
      </main>
    </div>
  )
}

export default App
