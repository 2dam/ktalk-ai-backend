import Auth from './Auth'
import ktalkLogo from './assets/ktalk-logo.png'

const WELCOME_FEATURES = [
  { icon: '💬', copy: 'AI와 실시간으로 대화하며 연습' },
  { icon: '🎯', copy: '내 실력에 맞춘 맞춤 학습' },
  { icon: '🔥', copy: '하루 5분, 부담 없이 꾸준히' },
]

const WELCOME_PLANS = [
  { name: 'Pro', price: '$9.90', label: '개인 학습자' },
  { name: 'Business', price: '$19.90', label: '강사와 팀' },
]

function WelcomeScreen({ authMode, onStart, onLogin, onLoginSuccess, onBack }) {
  return (
    <div className="welcome-shell">
      <div className="welcome-card glass-card">
        {authMode ? (
          <>
            <button type="button" className="welcome-back" onClick={onBack} aria-label="뒤로">
              ← 뒤로
            </button>
            <Auth onLogin={onLoginSuccess} initialMode={authMode} />
          </>
        ) : (
          <>
            <div className="welcome-header">
              <div className="welcome-brand">
                <img src={ktalkLogo} alt="" className="welcome-logo" />
                <span>ktalk</span>
              </div>
              <div className="welcome-header-links">
                <button type="button" onClick={onLogin}>로그인</button>
                <button type="button" onClick={onStart}>회원가입</button>
              </div>
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

            <div className="welcome-pricing" aria-label="요금제">
              {WELCOME_PLANS.map((plan) => (
                <div className="welcome-plan" key={plan.name}>
                  <span>{plan.name}</span>
                  <strong>{plan.price}</strong>
                  <small>{plan.label}</small>
                </div>
              ))}
            </div>
          </>
        )}
      </div>
    </div>
  )
}

export default WelcomeScreen
