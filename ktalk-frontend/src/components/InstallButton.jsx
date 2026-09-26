import { useEffect, useState } from 'react'

const isStandalone = () =>
  window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone === true
const isIos = () => /iphone|ipad|ipod/i.test(window.navigator.userAgent)

/**
 * "앱 설치"(홈 화면에 바로가기 추가) 버튼. 크롬/엣지/안드로이드는 브라우저가 알려주는
 * 설치 프롬프트를 띄우고, 프롬프트가 없는 iOS 사파리는 수동 안내 문구를 보여준다.
 * 이미 설치된 앱(standalone)으로 열렸으면 아무것도 그리지 않는다.
 */
function InstallButton() {
  const [installEvent, setInstallEvent] = useState(null)
  const [showIosHelp, setShowIosHelp] = useState(false)
  const [installed, setInstalled] = useState(() => isStandalone())

  useEffect(() => {
    const onPrompt = (event) => {
      event.preventDefault()
      setInstallEvent(event)
    }
    const onInstalled = () => { setInstalled(true); setInstallEvent(null) }
    window.addEventListener('beforeinstallprompt', onPrompt)
    window.addEventListener('appinstalled', onInstalled)
    return () => {
      window.removeEventListener('beforeinstallprompt', onPrompt)
      window.removeEventListener('appinstalled', onInstalled)
    }
  }, [])

  if (installed || (!installEvent && !isIos())) return null

  const handleClick = async () => {
    if (installEvent) {
      installEvent.prompt()
      await installEvent.userChoice
      setInstallEvent(null)
    } else {
      setShowIosHelp((value) => !value)
    }
  }

  return (
    <span style={{ position: 'relative' }}>
      <button type="button" className="login-button" onClick={handleClick}>앱 설치</button>
      {showIosHelp && (
        <span
          role="status"
          style={{
            position: 'absolute', right: 0, top: '110%', zIndex: 50, width: '220px', padding: '10px 12px',
            fontSize: '12px', lineHeight: 1.5, background: '#fff', border: '1px solid #ddd',
            borderRadius: '8px', boxShadow: '0 6px 16px rgba(0,0,0,.12)', textAlign: 'left',
          }}
        >
          사파리 하단의 공유 버튼(□↑)을 누른 뒤 <b>"홈 화면에 추가"</b>를 선택하세요.
        </span>
      )}
    </span>
  )
}

export default InstallButton
