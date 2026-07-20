import { useEffect, useMemo, useState } from 'react'
import axios from 'axios'
import { AuthCard } from './WelcomeScreen'
import TopikPage from './components/TopikPage'
import LearningNavigation from './components/LearningNavigation'
import RecommendedChannels from './components/RecommendedChannels'
import { AUTH_URL } from './api'
import ktalkLogo from './assets/ktalk-logo.png'
import './App.css'

// Learning Navigation이 전체 학습 과정을 아우르는 하나의 방법론이 되면서,
// 예전에 독립된 탭이었던 기능들은 이제 그 방법론의 어느 단계에 속하는 도구인지로
// 재배치된다. 옛 tabId를 남겨두는 건 TopikPage/히어로/가격 섹션 등 기존 진입점들이
// 그대로 동작하게 하기 위함이다 — 그 버튼들은 여전히 tabId를 넘기고, 여기서 그걸
// 해당 단계 + 도구로 해석한다.
const TAB_TO_STAGE = {
  assessment: { stage: 'interest', tool: 'assessment' },
  contents: { stage: 'infer', tool: 'contents' },
  clip: { stage: 'infer', tool: 'clip' },
  chat: { stage: 'pattern', tool: 'chat' },
  personalized: { stage: 'sensory', tool: 'personalized' },
  pronunciation: { stage: 'sensory', tool: 'pronunciation' },
}

const WEEK_METRICS = [
  { label: '정답률', tabId: 'assessment' },
  { label: '취약 단원', tabId: 'personalized' },
  { label: '수업참여도', tabId: 'clip' },
  { label: '모의고사', tabId: 'chat' },
  { label: '금주복습', tabId: 'personalized' },
]

const missionCards = [
  { title: '오늘의 미션', value: '12개', copy: '오늘 복습할 표현', tone: 'mint' },
  { title: '실전복습', value: '3분', copy: 'AI와 바로 말하기', tone: 'blue' },
  { title: '오답노트', value: '7개', copy: '다시 볼 표현', tone: 'rose' },
  { title: 'AI 발음 코치', value: '92점', copy: '최근 발음 정확도', tone: 'violet' },
  { title: '추천 유튜브 학습', value: '5개', copy: '내 수준 맞춤 클립', tone: 'amber' },
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
  const [navTarget, setNavTarget] = useState(null)
  const [showPasswordForm, setShowPasswordForm] = useState(false)
  const [passwordForm, setPasswordForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' })
  const [passwordChanging, setPasswordChanging] = useState(false)
  const [route, setRoute] = useState(() => window.location.pathname)
  const [pendingScroll, setPendingScroll] = useState(null)
  const [showAuth, setShowAuth] = useState(false)

  // 로그인 모달이 열려 있을 때 Esc로 닫기
  useEffect(() => {
    if (!showAuth) return
    const onKey = (event) => { if (event.key === 'Escape') setShowAuth(false) }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [showAuth])

  useEffect(() => {
    const handlePopState = () => setRoute(window.location.pathname)
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  // 라우트/탭 전환과 같은 렌더에서 스크롤을 요청하면 대상 엘리먼트가
  // 아직 DOM에 없을 수 있다. 커밋 이후에 실행되는 effect에서 스크롤해야
  // requestAnimationFrame 타이밍에 기대지 않고 항상 최신 DOM을 스크롤한다.
  useEffect(() => {
    if (!pendingScroll) return
    document.getElementById(pendingScroll)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    setPendingScroll(null)
  }, [pendingScroll, route, navTarget])

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const tokenFromRedirect = params.get('token')

    if (window.location.pathname === '/oauth2/redirect' && tokenFromRedirect) {
      localStorage.setItem('token', tokenFromRedirect)
      window.history.replaceState({}, '', '/')
    }

    const tabParam = params.get('tab')
    if (tabParam && TAB_TO_STAGE[tabParam]) {
      setNavTarget(TAB_TO_STAGE[tabParam])
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

  const navigateTo = (path) => {
    window.history.pushState({}, '', path)
    setRoute(path)
    window.scrollTo({ top: 0, behavior: 'instant' })
  }

  const goToSection = (id) => (event) => {
    event.preventDefault()
    if (route !== '/') {
      navigateTo('/')
    }
    setPendingScroll(id)
  }

  const jumpToExperience = (tabId = 'contents') => {
    if (route !== '/') {
      navigateTo('/')
    }
    setNavTarget(TAB_TO_STAGE[tabId] || { stage: 'interest' })
    setPendingScroll('ai-experience')
  }

  const jumpToTopikCourse = () => {
    if (route !== '/') {
      navigateTo('/')
    }
    setPendingScroll('topik-course')
  }

  const goToTopikPage = (event) => {
    event?.preventDefault()
    navigateTo('/topik')
  }

  // 인증 확인이 끝나기 전에는 웰컴 화면도, 기존 화면도 아닌 빈 배경만
  // 보여준다 — 안 그러면 이미 로그인된 사용자에게도 웰컴 화면이 잠깐
  // 번쩍이고 사라지는 깜빡임이 생긴다.
  if (!authChecked) {
    return <div className="welcome-shell" />
  }

  return (
    <div className="ktalk-shell">
      <header className="site-header">
        <a className="brand" href="/" onClick={(event) => { event.preventDefault(); navigateTo('/') }} aria-label="K-Talk AI 홈">
          <img src={ktalkLogo} alt="" className="brand-logo" />
          <span>
            <strong>K-Talk AI</strong>
            <small>Learn with clips and conversation</small>
          </span>
        </a>

        <nav className="header-nav" aria-label="주요 메뉴">
          <a href="#features" onClick={goToSection('features')}>기능</a>
          <a href="#ai-experience" onClick={goToSection('ai-experience')}>복습</a>
          <a
            href="/topik"
            className={`topik-nav-link ${route === '/topik' ? 'active' : ''}`}
            onClick={goToTopikPage}
          >
            TOPIK
          </a>
        </nav>

        <div className="header-actions">
          {isLoggedIn ? (
            <div className="user-chip">
              <span>{user.username}</span>
              <button type="button" onClick={() => setShowPasswordForm((value) => !value)}>설정</button>
              <button type="button" onClick={handleLogout}>로그아웃</button>
            </div>
          ) : (
            <button type="button" className="login-button" onClick={() => setShowAuth(true)}>
              로그인 / 회원가입
            </button>
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

      {route === '/topik' ? (
        <TopikPage
          onSelectTab={jumpToExperience}
          onSelectLevel={jumpToTopikCourse}
          onBack={() => navigateTo('/')}
        />
      ) : (
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
                <button type="button" className="topik-badge" onClick={goToTopikPage}>TOPIK 코스</button>
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
              {WEEK_METRICS.map((metric, index) => (
                <button
                  key={metric.label}
                  type="button"
                  className={index === 0 ? 'active' : ''}
                  onClick={() => jumpToExperience(metric.tabId)}
                >
                  {metric.label}
                </button>
              ))}
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
            <span className="eyebrow">Learning Navigation</span>
            <h2>말하고, 틀리고, 다시 익히는 학습 루프</h2>
          </div>
          {isLoggedIn ? (
            <div className="tool-surface glass-card" style={{ textAlign: 'center', padding: '48px 24px' }}>
              <p style={{ margin: '0 0 16px', color: 'var(--k-muted)' }}>
                진행 중인 Learning Navigation은 아래 AI Learning Lab에서 이어갈 수 있어요.
              </p>
              <button type="button" className="primary-cta" onClick={goToSection('ai-experience')}>
                AI Learning Lab로 이동 →
              </button>
            </div>
          ) : (
            <div className="tool-surface glass-card">
              <LearningNavigation />
            </div>
          )}
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

          {isLoggedIn ? (
            <div className="tool-surface glass-card">
              <LearningNavigation target={navTarget} />
            </div>
          ) : (
            <div className="auth-inline-wrap">
              <div className="auth-cta-card glass-card">
                <h3>로그인하고 학습을 이어가세요</h3>
                <p>학습 유형 진단 결과와 복습 기록이 저장돼, 다음에 이어서 학습할 수 있어요.</p>
                <button type="button" className="primary-cta" onClick={() => setShowAuth(true)}>
                  로그인 / 회원가입
                </button>
              </div>
            </div>
          )}

          <RecommendedChannels />
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
      )}

      {showAuth && !isLoggedIn && (
        <div className="auth-modal-overlay" onClick={() => setShowAuth(false)}>
          <div className="auth-modal" onClick={(event) => event.stopPropagation()}>
            <AuthCard
              compact
              onClose={() => setShowAuth(false)}
              onAuthenticated={(loggedInUser) => { setUser(loggedInUser); setShowAuth(false) }}
            />
          </div>
        </div>
      )}

      <footer className="site-footer">
        <span>© 2026 K-Talk AI · Made with ♥ in Seoul</span>
        <span>K-콘텐츠 기반 AI 한국어 학습</span>
      </footer>
    </div>
  )
}

export default App
