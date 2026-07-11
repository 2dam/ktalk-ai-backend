import ktalkLogo from './assets/ktalk-logo.png'

const WELCOME_FEATURES = [
  { icon: '💬', copy: 'AI와 실시간으로 대화하며 연습' },
  { icon: '🎯', copy: '내 실력에 맞춘 맞춤 학습' },
  { icon: '🔥', copy: '하루 5분, 부담 없이 꾸준히' },
]

function WelcomeScreen({ onStart, onLogin }) {
  return (
    <div className="welcome-shell">
      <div className="welcome-card glass-card">
        <div className="welcome-brand">
          <img src={ktalkLogo} alt="" className="welcome-logo" />
          <span>ktalk</span>
        </div>

        <div className="welcome-icon">💬</div>

        <h1 className="welcome-title">
          매일 5분,
          <br />
          AI와 진짜 한국어 회화
        </h1>

        <div className="welcome-features">
          {WELCOME_FEATURES.map((item) => (
            <div className="value-item" key={item.copy}>
              <span>{item.icon}</span>
              <span>{item.copy}</span>
            </div>
          ))}
        </div>

        <button type="button" className="primary-cta welcome-cta" onClick={onStart}>
          무료로 시작하기
        </button>
        <button type="button" className="welcome-secondary" onClick={onLogin}>
          이미 계정이 있어요
        </button>
      </div>
    </div>
  )
}

export default WelcomeScreen
