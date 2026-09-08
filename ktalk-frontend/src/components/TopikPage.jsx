import { useState } from 'react'
import TopikQuiz from './TopikQuiz'
import TodayCurriculum from './TodayCurriculum'
import TopikWeekBrowser from './TopikWeekBrowser'
import WrongNoteList from './WrongNoteList'

const CONTENT_ITEMS = [
  { label: '기출문제집', icon: '📘', desc: '실제 시험과 같은 난이도의 기출문제로 실전 감각을 익혀요.', view: 'exam-bank' },
  { label: '모의고사', icon: '⏱', desc: '시간 제한 모의고사로 실전처럼 풀어보고 점수를 확인해요.', view: 'mock-exam' },
  { label: '오답노트', icon: '📝', desc: '틀린 문제만 모아 반복 학습하고 취약점을 보완해요.', view: 'wrong-notes' },
  { label: '학습 유형 진단', icon: '🎯', desc: '20문항 진단으로 나에게 맞는 학습 전략을 찾아요.', tabId: 'assessment' },
]

const LEVEL_ITEMS = [
  { label: '1~2급', desc: '초급 학습자를 위한 기초 어휘·문법 커리큘럼' },
  { label: '3~4급', desc: '중급 학습자를 위한 실전 독해·듣기 커리큘럼' },
  { label: '5~6급', desc: '고급 학습자를 위한 심화 작문·토론 커리큘럼' },
]

// 모의고사/Final 예상문제 주차만 이 패턴으로 구분한다(제목 규칙은 각 학습유형
// CurriculumDataLoader의 mockExam1()/mockExam2()/finalExam1()에서 정한 것과 동일).
const isExamWeek = (week) => week.title.includes('모의고사') || week.title.startsWith('Final')

function TopikPage({ initialView = 'menu', onSelectTab, onBack, onRequireAuth }) {
  const [view, setView] = useState(initialView)

  if (view === 'quiz') {
    return <TopikQuiz onBack={() => setView('menu')} onRequireAuth={onRequireAuth} />
  }
  if (view === 'curriculum') {
    return (
      <TodayCurriculum
        onBack={() => setView('menu')}
        onRequireAuth={onRequireAuth}
        onGoToAssessment={() => onSelectTab('assessment')}
      />
    )
  }
  if (view === 'exam-bank') {
    return (
      <TopikWeekBrowser
        heading="기출문제집"
        description="주차별로 정리된 기출문제 스타일 문제를 골라서 풀어보세요."
        filterWeeks={(weeks) => weeks.filter((week) => !isExamWeek(week))}
        emptyMessage="아직 이 학습유형에는 기출문제집 콘텐츠가 준비되지 않았어요."
        onBack={() => setView('menu')}
        onRequireAuth={onRequireAuth}
        onGoToAssessment={() => onSelectTab('assessment')}
      />
    )
  }
  if (view === 'mock-exam') {
    return (
      <TopikWeekBrowser
        heading="모의고사"
        description="실전 모의고사·Final 예상문제를 골라서 시간 제한 없이 풀어보세요."
        filterWeeks={(weeks) => weeks.filter(isExamWeek)}
        emptyMessage="아직 이 학습유형에는 모의고사 콘텐츠가 준비되지 않았어요."
        onBack={() => setView('menu')}
        onRequireAuth={onRequireAuth}
        onGoToAssessment={() => onSelectTab('assessment')}
      />
    )
  }
  if (view === 'wrong-notes') {
    return <WrongNoteList onBack={() => setView('menu')} onRequireAuth={onRequireAuth} />
  }

  return (
    <main className="topik-page" id="top">
      <div className="topik-page-head">
        <button type="button" className="topik-back" onClick={onBack}>← 홈으로</button>
        <span className="topik-badge">TOPIK 코스</span>
        <h1>나에게 맞는 TOPIK 학습을 선택하세요</h1>
        <p>기출문제, 모의고사, 오답노트부터 급수별 코스까지 한 곳에서 시작해보세요.</p>
      </div>

      <section className="topik-page-group">
        <h2>오늘의 커리큘럼</h2>
        <button
          type="button"
          className="topik-page-card"
          onClick={() => setView('curriculum')}
          style={{ width: '100%', textAlign: 'left' }}
        >
          <span className="topik-page-card-icon">📅</span>
          <b>오늘의 학습 시작하기</b>
          <small>학습 유형 진단 결과에 맞춘 8주 커리큘럼을 매일 하나씩 진행해요.</small>
        </button>
      </section>

      <section className="topik-page-group">
        <h2>학습 콘텐츠</h2>
        <div className="topik-page-grid">
          {CONTENT_ITEMS.map((item) => (
            <button
              type="button"
              className="topik-page-card"
              key={item.label}
              onClick={() => (item.view ? setView(item.view) : onSelectTab(item.tabId))}
            >
              <span className="topik-page-card-icon">{item.icon}</span>
              <b>{item.label}</b>
              <small>{item.desc}</small>
            </button>
          ))}
        </div>
      </section>

      <section className="topik-page-group">
        <h2>급수별 코스</h2>
        <div className="topik-page-grid">
          {LEVEL_ITEMS.map((item) => (
            <button
              type="button"
              className="topik-page-card"
              key={item.label}
              onClick={() => setView('quiz')}
            >
              <b>{item.label}</b>
              <small>{item.desc}</small>
            </button>
          ))}
        </div>
      </section>
    </main>
  )
}

export default TopikPage
