import { useEffect, useMemo, useState } from 'react'
import axios from 'axios'
import { AuthCard } from './WelcomeScreen'
import TopikPage from './components/TopikPage'
import LearningNavigation from './components/LearningNavigation'
import RecommendedChannels from './components/RecommendedChannels'
import InstallButton from './components/InstallButton'
import { Bi, EnglishHintToggle } from './EnglishHint'
import { API_BASE, AUTH_URL, authHeaders } from './api'
import ktalkLogo from './assets/ktalk-logo.png'
import './App.css'

const CURRICULUM_URL = `${API_BASE}/api/curriculum`

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
  { label: '정답률', en: 'Accuracy', tabId: 'assessment' },
  { label: '취약 단원', en: 'Weak units', tabId: 'personalized' },
  { label: '수업참여도', en: 'Participation', tabId: 'clip' },
  { label: '모의고사', en: 'Mock exam', tabId: 'chat' },
  { label: '금주복습', en: 'Weekly review', tabId: 'personalized' },
]

// tabId가 있으면 jumpToExperience로, topikView가 있으면 TOPIK 코스의 해당 화면으로 이동한다.
// statKey가 있으면 로그인 시 실제 값을 불러와 value 대신 보여준다(비로그인/로딩
// 실패 시에는 value의 예시 숫자를 그대로 보여줘서 마케팅 화면이 비지 않게 한다).
const missionCards = [
  { title: '오늘의 미션', titleEn: "Today's mission", copyEn: 'Expressions to review today', value: '12개', copy: '오늘 복습할 표현', tone: 'mint', topikView: 'curriculum', statKey: 'todayCount', unit: '개' },
  { title: '실전복습', titleEn: 'Practice review', copyEn: 'Speak with AI right away', value: '3분', copy: 'AI와 바로 말하기', tone: 'blue', tabId: 'chat' },
  { title: '오답노트', titleEn: 'Wrong-answer notes', copyEn: 'Expressions to revisit', value: '7개', copy: '다시 볼 표현', tone: 'rose', topikView: 'wrong-notes', statKey: 'wrongCount', unit: '개' },
  { title: 'AI 발음 코치', titleEn: 'AI pronunciation coach', copyEn: 'Recent pronunciation accuracy', value: '92점', copy: '최근 발음 정확도', tone: 'violet', tabId: 'pronunciation' },
  { title: '추천 유튜브 학습', titleEn: 'Recommended YouTube lessons', copyEn: 'Clips matched to your level', value: '5개', copy: '내 수준 맞춤 클립', tone: 'amber', tabId: 'clip' },
]

const howSteps = [
  {
    kicker: '01 · DISCOVER',
    title: '나의 학습 유형 진단',
    titleEn: 'Find your learner type',
    desc: '생활습관, 집중력 패턴, 학습 동기 등 20문항으로 6가지 유형 중 나와 가장 가까운 유형을 찾아요.',
    descEn: 'Answer 20 questions about habits, focus and motivation to find your closest type of 6.',
    link: '약 5~7분 소요 →',
    linkEn: 'About 5-7 minutes →',
  },
  {
    kicker: '02 · PERSONALIZE',
    title: '교재와 8주 커리큘럼 추천',
    titleEn: 'Books and an 8-week plan',
    desc: '진단된 유형에 맞춰 교재·강의·앱과 단계별 커리큘럼을 자동으로 구성해요.',
    descEn: 'Textbooks, lectures, apps and a step-by-step plan are built for your type.',
    link: '완전 맞춤 구성 →',
    linkEn: 'Fully personalized →',
  },
  {
    kicker: '03 · GROW',
    title: '학습 → AI 대화 → 복습 루프',
    titleEn: 'Learn → talk with AI → review loop',
    desc: '유튜브 클립과 AI 회화·발음 코치로 실전 연습하고, 개인화 복습으로 다시 다져요.',
    descEn: 'Practice with YouTube clips and the AI conversation and pronunciation coach, then review.',
    link: '꾸준히 반복하기 →',
    linkEn: 'Keep repeating →',
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
  const [topikInitialView, setTopikInitialView] = useState('menu')
  const [missionStats, setMissionStats] = useState({})

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

  // 로그인된 사용자에게는 "오늘의 미션"/"오답노트" 카드에 실제 개수를 보여준다.
  // 진단을 아직 안 했거나 API가 실패하면 조용히 무시하고 예시 숫자를 그대로 둔다.
  useEffect(() => {
    if (!isLoggedIn) return
    const headers = authHeaders()
    axios.get(`${CURRICULUM_URL}/today`, { headers })
      .then((res) => {
        if (!res.data?.success) return
        const totalCount = (res.data.data.passages || [])
          .reduce((sum, passage) => sum + (passage.problems?.length || 0), 0)
        setMissionStats((prev) => ({ ...prev, todayCount: totalCount }))
      })
      .catch(() => {})
    axios.get(`${CURRICULUM_URL}/wrong-notes`, { headers })
      .then((res) => {
        if (!res.data?.success) return
        setMissionStats((prev) => ({ ...prev, wrongCount: res.data.data.length }))
      })
      .catch(() => {})
  }, [isLoggedIn])

  const trustPoints = useMemo(() => [
    { icon: '✓', title: '6가지 학습 유형', titleEn: '6 learner types', desc: '생활습관·집중력·동기를 함께 분석', descEn: 'Analyzes habits, focus and motivation' },
    { icon: '✦', title: 'TOPIK 맞춤 커리큘럼', titleEn: 'Personalized TOPIK plan', desc: '유형별 교재·강의·8주 학습 전략', descEn: 'Books, lectures and an 8-week strategy per type' },
    { icon: '↗', title: 'AI 회화·발음 코치', titleEn: 'AI speaking & pronunciation coach', desc: '매일 실전처럼 말하고 복습', descEn: 'Speak and review like the real thing, daily' },
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

  const goToTopikPage = (event) => {
    event?.preventDefault()
    setTopikInitialView('menu')
    navigateTo('/topik')
  }

  const goToTopikView = (view) => {
    setTopikInitialView(view)
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
          <a href="#ai-experience" onClick={goToSection('ai-experience')}><Bi en="Features">기능</Bi></a>
          <a href="#ai-experience" onClick={goToSection('ai-experience')}><Bi en="Review">복습</Bi></a>
          <a
            href="/topik"
            className={`topik-nav-link ${route === '/topik' ? 'active' : ''}`}
            onClick={goToTopikPage}
          >
            TOPIK
          </a>
        </nav>

        <div className="header-actions">
          <EnglishHintToggle />
          <InstallButton />
          {isLoggedIn ? (
            <div className="user-chip">
              <span>{user.username}</span>
              <button type="button" onClick={() => setShowPasswordForm((value) => !value)}><Bi en="Settings">설정</Bi></button>
              <button type="button" onClick={handleLogout}><Bi en="Log out">로그아웃</Bi></button>
            </div>
          ) : (
            <button type="button" className="login-button" onClick={() => setShowAuth(true)}>
              <Bi en="Log in / Sign up">로그인 / 회원가입</Bi>
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
          initialView={topikInitialView}
          onSelectTab={jumpToExperience}
          onBack={() => navigateTo('/')}
          onRequireAuth={() => setShowAuth(true)}
        />
      ) : (
      <main id="top">
        <section className="hero-section">
          <div className="hero-copy">
            <p className="eyebrow">K-CONTENT × PERSONAL AI</p>
            <h1>
              좋아하는 콘텐츠로,<br /><em>나답게 배우는</em> 한국어.
              <Bi en="Learn Korean your way, through the content you love.">{''}</Bi>
            </h1>
            <p className="hero-subtitle">
              <Bi en="Take a 20-question learner-type test, get matched books and an 8-week plan, then practice real conversation with AI.">
                20문항 학습 유형 진단으로 나에게 맞는 교재와 8주 커리큘럼을 추천받고, AI와 함께 실전 회화까지 연습하세요.
              </Bi>
            </p>
            <div className="hero-actions">
              <button className="primary-cta" type="button" onClick={() => jumpToExperience('assessment')}>
                <Bi en="Start the free learner-type test →">무료 학습 유형 진단 시작하기 →</Bi>
              </button>
              <button className="secondary-cta" type="button" onClick={() => jumpToExperience('chat')}>
                <Bi en="Try AI conversation">AI 회화 체험하기</Bi>
              </button>
            </div>
            <div className="trust-row" aria-label="핵심 기능">
              {trustPoints.map((point) => (
                <div className="mini-stat" key={point.title}>
                  <span>{point.icon}</span>
                  <div>
                    <strong><Bi en={point.titleEn}>{point.title}</Bi></strong>
                    <small><Bi en={point.descEn}>{point.desc}</Bi></small>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <aside className="dashboard-preview glass-card" id="topik-course" aria-label="학습 유형 진단 결과 미리보기">
            <div className="preview-top">
              <div>
                <button type="button" className="topik-badge" onClick={goToTopikPage}>TOPIK 코스</button>
                <h2><Bi en="Your personalized learning">회원님의 맞춤 학습</Bi></h2>
              </div>
              <span className="streak-badge">🧠</span>
            </div>
            <div className="progress-ring" aria-label="진단된 학습 유형">
              <span className="type-label">STRATEGIC ANALYST</span>
              <span><Bi en="Strategic Analyst">전략적 분석가</Bi></span>
              <small><Bi en="A self-directed learner who plans well and analyzes mistakes.">스스로 계획하고 오답을 분석하는 능력이 뛰어난 자기주도형 학습자예요.</Bi></small>
            </div>
            <div className="preview-grid">
              <div className="cover">
                <small><Bi en="Personalized TOPIK book">TOPIK 맞춤 교재</Bi></small>
                <b>3~4급</b>
                <span>PLAN &amp; ANALYZE</span>
              </div>
              <div className="book-copy">
                <p className="eyebrow"><Bi en="This month's book">이번 달 교재</Bi></p>
                <h3><Bi en="Past-exam workbook + wrong-answer notebook">기출문제집 + 오답 노트 전용 교재</Bi></h3>
                <p><Bi en="Past exams x5 · weak areas turned into data">기출 5회독 · 영역별 약점 데이터화</Bi></p>
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
                  <Bi en={metric.en}>{metric.label}</Bi>
                </button>
              ))}
            </div>
            <button type="button" className="start-review" onClick={() => jumpToExperience('assessment')}>
              <Bi en="Find my learner type">내 학습 유형 진단하기</Bi>
            </button>
          </aside>
        </section>

        <section className="mission-strip" aria-label="오늘의 학습 카드">
          {missionCards.map((card) => {
            const liveCount = card.statKey ? missionStats[card.statKey] : null
            const displayValue = liveCount != null ? `${liveCount}${card.unit || ''}` : card.value
            return (
              <button
                type="button"
                className={`mission-card ${card.tone}`}
                key={card.title}
                onClick={() => (card.topikView ? goToTopikView(card.topikView) : jumpToExperience(card.tabId))}
              >
                <span><Bi en={card.titleEn}>{card.title}</Bi></span>
                <strong>{displayValue}</strong>
                <small><Bi en={card.copyEn}>{card.copy}</Bi></small>
              </button>
            )
          })}
        </section>

        <section className="loop-section">
          <div className="how-head">
            <span className="eyebrow">HOW K-TALK WORKS</span>
            <h2><Bi en="From the test to a learning loop">진단부터 학습 루프까지</Bi></h2>
            <p><Bi en="We do not stop at recommendations - your results turn into a real learning flow.">추천만 하고 끝나지 않아요. 진단 결과가 실제 학습 흐름으로 이어집니다.</Bi></p>
          </div>
          <div className="loop-steps">
            {howSteps.map((step) => (
              <article key={step.kicker}>
                <span className="step-kicker">{step.kicker}</span>
                <span className="step-title"><Bi en={step.titleEn}>{step.title}</Bi></span>
                <span className="step-desc"><Bi en={step.descEn}>{step.desc}</Bi></span>
                <span className="step-link"><Bi en={step.linkEn}>{step.link}</Bi></span>
              </article>
            ))}
          </div>
        </section>

        <section className="section-block learning-board" id="ai-experience">
          <div className="section-heading">
            <span className="eyebrow">AI Learning Lab</span>
            <h2><Bi en="Keep learning today">오늘도 이어서 학습해보세요</Bi></h2>
            <p><Bi en="Content creation, YouTube learning, conversation and pronunciation coaching - all right here.">콘텐츠 생성, 유튜브 학습, 회화, 발음 코치까지 모든 기능을 여기서 바로 사용할 수 있습니다.</Bi></p>
          </div>

          <div className="tool-surface glass-card">
            <LearningNavigation target={navTarget} onRequireAuth={() => setShowAuth(true)} />
          </div>

          <RecommendedChannels />
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
        <span><Bi en="AI Korean learning through K-content">K-콘텐츠 기반 AI 한국어 학습</Bi></span>
      </footer>
    </div>
  )
}

export default App
