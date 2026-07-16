import { useEffect, useMemo, useRef, useState } from 'react'
import axios from 'axios'
import WelcomeScreen from './WelcomeScreen'
import ContentManager from './components/ContentManager'
import ClipAndLearn from './components/ClipAndLearn'
import CharacterChat from './components/CharacterChat'
import PronunciationCoach from './components/PronunciationCoach'
import PersonalizedLearning from './components/PersonalizedLearning'
import AssessmentSurvey from './components/AssessmentSurvey'
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
  { id: 'assessment', label: '학습 유형 진단', short: '유형진단' },
]

const TOPIK_MENU_GROUPS = [
  {
    label: '학습 콘텐츠',
    items: [
      { label: '기출문제집', tabId: 'contents' },
      { label: '모의고사', tabId: 'chat' },
      { label: '오답노트', tabId: 'personalized' },
      { label: '학습 유형 진단', tabId: 'assessment' },
    ],
  },
  {
    label: '급수별 코스',
    items: [
      { label: '1~2급', anchorId: 'topik-course' },
      { label: '3~4급', anchorId: 'topik-course' },
      { label: '5~6급', anchorId: 'topik-course' },
    ],
  },
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
    title: '학습 유형 진단',
    copy: '20문항 진단으로 6가지 유형 중 나에게 맞는 유형을 찾고, 맞춤 교재·커리큘럼을 추천받습니다.',
  },
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

const howSteps = [
  {
    kicker: '01 · DISCOVER',
    title: '나의 학습 유형 진단',
    desc: '생활습관, 집중력 패턴, 학습 동기 등 20문항으로 6가지 유형 중 나와 가장 가까운 유형을 찾아요.',
    link: '약 5~7분 소요 →',
  },
  {
    kicker: '02 · PERSONALIZE',
    title: '교재와 8주 커리큘럼 추천',
    desc: '진단된 유형에 맞춰 교재·강의·앱과 단계별 커리큘럼을 자동으로 구성해요.',
    link: '완전 맞춤 구성 →',
  },
  {
    kicker: '03 · GROW',
    title: '학습 → AI 대화 → 복습 루프',
    desc: '유튜브 클립과 AI 회화·발음 코치로 실전 연습하고, 개인화 복습으로 다시 다져요.',
    link: '꾸준히 반복하기 →',
  },
]

function App() {
  const [user, setUser] = useState(null)
  const [authChecked, setAuthChecked] = useState(false)
  const [activeTab, setActiveTab] = useState('contents')
  const [showPasswordForm, setShowPasswordForm] = useState(false)
  const [passwordForm, setPasswordForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' })
  const [passwordChanging, setPasswordChanging] = useState(false)
  const [topikMenuOpen, setTopikMenuOpen] = useState(false)
  const topikMenuRef = useRef(null)

  useEffect(() => {
    if (!topikMenuOpen) return

    const handleClickOutside = (event) => {
      if (topikMenuRef.current && !topikMenuRef.current.contains(event.target)) {
        setTopikMenuOpen(false)
      }
    }

    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [topikMenuOpen])

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

  const isLoggedIn = authChecked && !!user

  const trustPoints = useMemo(() => [
    { icon: '✓', title: '6가지 학습 유형', desc: '생활습관·집중력·동기를 함께 분석' },
    { icon: '✦', title: 'TOPIK 맞춤 커리큘럼', desc: '유형별 교재·강의·8주 학습 전략' },
    { icon: '↗', title: 'AI 회화·발음 코치', desc: '매일 실전처럼 말하고 복습' },
  ], [])

  const handleLogout = () => {
    localStorage.removeItem('token')
    setUser(null)
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

  const handleTopikMenuSelect = (item) => {
    if (item.tabId) {
      jumpToExperience(item.tabId)
    } else if (item.anchorId) {
      document.getElementById(item.anchorId)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
    setTopikMenuOpen(false)
  }

  // 인증 확인이 끝나기 전에는 웰컴 화면도, 기존 화면도 아닌 빈 배경만
  // 보여준다 — 안 그러면 이미 로그인된 사용자에게도 웰컴 화면이 잠깐
  // 번쩍이고 사라지는 깜빡임이 생긴다.
  if (!authChecked) {
    return <div className="welcome-shell" />
  }

  // 로그인 전 방문자는 전체 기능 화면 대신 로그인/회원가입 폼이 있는
  // 웰컴 화면만 본다. 진단 결과 등은 실제 로그인된 사용자에게만 연결된다.
  if (!isLoggedIn) {
    return <WelcomeScreen onAuthenticated={(loggedInUser) => setUser(loggedInUser)} />
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
          <div className="topik-menu" ref={topikMenuRef}>
            <button
              type="button"
              className={`topik-menu-trigger ${topikMenuOpen ? 'open' : ''}`}
              aria-expanded={topikMenuOpen}
              aria-haspopup="true"
              onClick={() => setTopikMenuOpen((value) => !value)}
            >
              TOPIK <span>▾</span>
            </button>
            {topikMenuOpen && (
              <div className="topik-dropdown" role="menu">
                {TOPIK_MENU_GROUPS.map((group) => (
                  <div className="topik-dropdown-group" key={group.label}>
                    <span className="topik-dropdown-label">{group.label}</span>
                    {group.items.map((item) => (
                      <button
                        type="button"
                        role="menuitem"
                        key={item.label}
                        onClick={() => handleTopikMenuSelect(item)}
                      >
                        {item.label}
                      </button>
                    ))}
                  </div>
                ))}
              </div>
            )}
          </div>
        </nav>

        <div className="header-actions">
          <div className="user-chip">
            <span>{user.username}</span>
            <button type="button" onClick={() => setShowPasswordForm((value) => !value)}>설정</button>
            <button type="button" onClick={handleLogout}>로그아웃</button>
          </div>
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
            <p className="eyebrow">K-CONTENT × PERSONAL AI</p>
            <h1>좋아하는 콘텐츠로,<br /><em>나답게 배우는</em> 한국어.</h1>
            <p className="hero-subtitle">
              20문항 학습 유형 진단으로 나에게 맞는 교재와 8주 커리큘럼을 추천받고, AI와 함께 실전 회화까지 연습하세요.
            </p>
            <div className="hero-actions">
              <button className="primary-cta" type="button" onClick={() => jumpToExperience('assessment')}>
                무료 학습 유형 진단 시작하기 →
              </button>
              <button className="secondary-cta" type="button" onClick={() => jumpToExperience('chat')}>
                AI 회화 체험하기
              </button>
            </div>
            <div className="trust-row" aria-label="핵심 기능">
              {trustPoints.map((point) => (
                <div className="mini-stat" key={point.title}>
                  <span>{point.icon}</span>
                  <div>
                    <strong>{point.title}</strong>
                    <small>{point.desc}</small>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <aside className="dashboard-preview glass-card" id="topik-course" aria-label="학습 유형 진단 결과 미리보기">
            <div className="preview-top">
              <div>
                <span className="preview-kicker">TOPIK 코스</span>
                <h2>회원님의 맞춤 학습</h2>
              </div>
              <span className="streak-badge">🧠</span>
            </div>
            <div className="progress-ring" aria-label="진단된 학습 유형">
              <span className="type-label">STRATEGIC ANALYST</span>
              <span>전략적 분석가</span>
              <small>스스로 계획하고 오답을 분석하는 능력이 뛰어난 자기주도형 학습자예요.</small>
            </div>
            <div className="preview-grid">
              <div className="cover">
                <small>TOPIK 맞춤 교재</small>
                <b>3~4급</b>
                <span>PLAN &amp; ANALYZE</span>
              </div>
              <div className="book-copy">
                <p className="eyebrow">이번 달 교재</p>
                <h3>기출문제집 + 오답 노트 전용 교재</h3>
                <p>기출 5회독 · 영역별 약점 데이터화</p>
              </div>
            </div>
            <div className="week-grid">
              <span>정답률</span>
              <span>취약 단원</span>
              <span>수업참여도</span>
              <span>모의고사</span>
              <span>금주복습</span>
            </div>
            <button type="button" className="start-review" onClick={() => jumpToExperience('assessment')}>
              내 학습 유형 진단하기
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

        <section className="loop-section">
          <div className="how-head">
            <span className="eyebrow">HOW K-TALK WORKS</span>
            <h2>진단부터 학습 루프까지</h2>
            <p>추천만 하고 끝나지 않아요. 진단 결과가 실제 학습 흐름으로 이어집니다.</p>
          </div>
          <div className="loop-steps">
            {howSteps.map((step) => (
              <article key={step.kicker}>
                <span className="step-kicker">{step.kicker}</span>
                <span className="step-title">{step.title}</span>
                <span className="step-desc">{step.desc}</span>
                <span className="step-link">{step.link}</span>
              </article>
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
            {activeTab === 'assessment' && <AssessmentSurvey />}
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

      <footer className="site-footer">
        <span>© 2026 K-Talk AI · Made with ♥ in Seoul</span>
        <span>K-콘텐츠 기반 AI 한국어 학습</span>
      </footer>
    </div>
  )
}

export default App
