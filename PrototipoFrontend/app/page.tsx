'use client'

import { useEffect, useRef, useState } from 'react'
import {
  ArrowLeft,
  Check,
  ChevronDown,
  ChevronRight,
  Clock3,
  Contrast,
  History,
  Info,
  Minus,
  Mic,
  Moon,
  Play,
  Plus,
  RotateCcw,
  Send,
  Settings2,
  Sparkles,
  Sun,
  Type,
  Volume2,
  X,
} from 'lucide-react'

const starterHistory = ['Sí', '¿Cómo estás?', 'Buenos días', 'Muchas gracias', 'Necesito ayuda']

const glossFor = (text: string) => {
  if (text.toLowerCase().includes('universidad')) return 'YO UNIVERSIDAD IR QUERER'
  if (text.toLowerCase().includes('gracias')) return 'GRACIAS MUCHO'
  if (text.toLowerCase().includes('ayuda')) return 'YO AYUDA NECESITAR'
  if (text.toLowerCase().includes('cómo')) return 'TÚ CÓMO ESTAR'
  return text.toUpperCase().replace(/[¿?¡!]/g, '') || 'ESPERANDO MENSAJE'
}

type Side = 'left' | 'right' | null

type State = 'idle' | 'listening' | 'processing' | 'translating' | 'signing' | 'done'
type Character = 'Julián' | 'Samuel' | 'Ximena' | 'Gris'

export default function Page() {
  const [loaded, setLoaded] = useState(false)
  const [progress, setProgress] = useState(0)
  const [dark, setDark] = useState(false)
  const [input, setInput] = useState('')
  const [lastText, setLastText] = useState('')
  const [history, setHistory] = useState(starterHistory)
  const [state, setState] = useState<State>('idle')
  const [historyOpen, setHistoryOpen] = useState(false)
  const [side, setSide] = useState<Side>(null)
  const [about, setAbout] = useState(false)
  const [bold, setBold] = useState(false)
  const [large, setLarge] = useState(false)
  const [contrast, setContrast] = useState(false)
  const [reduceMotion, setReduceMotion] = useState(false)
  const [character, setCharacter] = useState<Character>('Gris')
  const [speed, setSpeed] = useState(50)
  const [dragSide, setDragSide] = useState<'left' | 'right' | null>(null)
  const [dragOffset, setDragOffset] = useState(0)
  const startX = useRef<number | null>(null)
  const dragRef = useRef<'left' | 'right' | null>(null)

  useEffect(() => {
    const timer = window.setInterval(() => setProgress((value) => Math.min(value + 5, 100)), 55)
    return () => window.clearInterval(timer)
  }, [])

  useEffect(() => {
    if (progress >= 100) {
      const timeout = window.setTimeout(() => setLoaded(true), 220)
      return () => window.clearTimeout(timeout)
    }
  }, [progress])

  useEffect(() => {
    if (state !== 'listening') return
    const timer = window.setTimeout(() => {
      setInput('Quiero ir a la universidad')
      setState('processing')
      window.setTimeout(() => setState('idle'), 800)
    }, 1500)
    return () => window.clearTimeout(timer)
  }, [state])

  const send = (phrase = input) => {
    const clean = phrase.trim()
    if (!clean || state === 'translating' || state === 'signing') return
    setInput(clean)
    setLastText(clean)
    setHistory((items) => [clean, ...items.filter((item) => item.toLowerCase() !== clean.toLowerCase())].slice(0, 5))
    setState('translating')
    window.setTimeout(() => setState('signing'), 850)
    window.setTimeout(() => setState('done'), 2700)
  }

  const status = {
    idle: 'Listo para traducir',
    listening: 'Escuchando...',
    processing: 'Procesando...',
    translating: 'Traduciendo...',
    signing: 'Reproduciendo señas...',
    done: 'Reproducción completada',
  }[state]

  const handleTouchStart = (event: React.TouchEvent) => {
    startX.current = event.touches[0]?.clientX ?? null
  }
  const handleTouchEnd = (event: React.TouchEvent) => {
    if (startX.current === null) return
    const endX = event.changedTouches[0]?.clientX ?? startX.current
    const delta = endX - startX.current
    if (Math.abs(delta) > 58) {
      if (delta > 0 && startX.current < 54) setSide('left')
      if (delta < 0 && startX.current > window.innerWidth - 54) setSide('right')
    }
    startX.current = null
  }

  const beginEdgeDrag = (edge: 'left' | 'right', event: React.PointerEvent<HTMLButtonElement>) => {
    event.currentTarget.setPointerCapture(event.pointerId)
    dragRef.current = edge
    startX.current = event.clientX
    setDragSide(edge)
    setDragOffset(0)
  }
  const moveEdgeDrag = (event: React.PointerEvent<HTMLButtonElement>) => {
    if (!dragRef.current || startX.current === null) return
    const rawDelta = event.clientX - startX.current
    const signedDelta = dragRef.current === 'left' ? rawDelta : -rawDelta
    setDragOffset(Math.max(0, Math.min(300, signedDelta)))
  }
  const endEdgeDrag = () => {
    if (!dragRef.current) return
    const edge = dragRef.current
    if (dragOffset > 90) setSide(edge)
    dragRef.current = null
    startX.current = null
    setDragSide(null)
    setDragOffset(0)
  }

  if (!loaded) {
    return (
      <main className="splash-screen" aria-label="Cargando Seña">
        <div className="splash-logo">S</div>
        <p className="eyebrow">SEÑA</p>
        <div className="progress-track"><div className="progress-value" style={{ width: `${progress}%` }} /></div>
        <span className="loading-number">{progress}%</span>
      </main>
    )
  }

  if (about) {
    return (
      <main className={`app-shell ${dark ? 'theme-dark' : ''} ${bold ? 'font-bold' : ''} ${large ? 'font-large' : ''} ${contrast ? 'high-contrast' : ''}`}>
        <section className="about-screen">
          <button className="icon-button" onClick={() => setAbout(false)} aria-label="Volver"><ArrowLeft /></button>
          <div className="about-content">
            <div className="logo-tile">S</div>
            <p className="eyebrow">SEÑA</p>
            <h1>Seña</h1>
            <p className="about-copy">Una herramienta que transforma tus frases en español a Lengua de Señas Colombiana mediante un avatar 3D.</p>
            <div className="about-meta"><span>Proyecto de tesis</span><span>Pontificia Universidad Javeriana</span><span>Versión 1.0</span></div>
          </div>
        </section>
      </main>
    )
  }

  return (
    <main
      className={`app-shell ${dark ? 'theme-dark' : ''} ${bold ? 'font-bold' : ''} ${large ? 'font-large' : ''} ${contrast ? 'high-contrast' : ''}`}
      onTouchStart={handleTouchStart}
      onTouchEnd={handleTouchEnd}
    >
      {!side && <>
        <button className="edge-tab edge-tab-left" aria-label="Arrastrar para abrir conversación" onClick={() => setSide('left')} onPointerDown={(event) => beginEdgeDrag('left', event)} onPointerMove={moveEdgeDrag} onPointerUp={endEdgeDrag} onPointerCancel={endEdgeDrag}><span className="edge-grip" /><span className="sr-only">Conversación</span></button>
        <button className="edge-tab edge-tab-right" aria-label="Arrastrar para abrir ajustes" onClick={() => setSide('right')} onPointerDown={(event) => beginEdgeDrag('right', event)} onPointerMove={moveEdgeDrag} onPointerUp={endEdgeDrag} onPointerCancel={endEdgeDrag}><span className="edge-grip" /><span className="sr-only">Ajustes</span></button>
      </>}

      {dragSide && <div className={`drag-preview ${dragSide}`} style={{ '--drag-offset': `${dragOffset}px` } as React.CSSProperties}><span>{dragSide === 'left' ? 'Conversación' : 'Ajustes'}</span></div>}

      <section className="avatar-stage" aria-label="Área del avatar">
        <div className="stage-hint"><span className="live-dot" /> {status}</div>
        <div className={`avatar-scene character-${character.toLowerCase()} ${state === 'signing' ? 'is-signing' : ''}`}>
          <div className="scene-glow" />
          <div className="avatar-head"><div className="avatar-hair" /><div className="avatar-face"><i /><i /><b /></div></div>
          <div className="avatar-neck" />
          <div className="avatar-body"><div className="shoulder left" /><div className="shoulder right" /></div>
          <div className="avatar-arm arm-left" /><div className="avatar-arm arm-right" />
          <div className="avatar-hand hand-left" /><div className="avatar-hand hand-right" />
          <div className="scene-floor" />
        </div>
        <div className="stage-caption"><span>LSC</span><span className="caption-dot" /><span>Avatar 3D</span></div>
        {state === 'done' && <button className="repeat-button" onClick={() => send(lastText)}><RotateCcw /> Repetir</button>}
      </section>

      <section className="control-panel" aria-label="Controles de traducción">
        <div className="input-row">
          <input value={input} onChange={(event) => setInput(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter' && !event.nativeEvent.isComposing && event.keyCode !== 229) send() }} placeholder="Escribe una frase..." aria-label="Frase en español" />
          <button className="history-button" onClick={() => setHistoryOpen(true)} aria-label="Historial reciente"><History /></button>
        </div>
        <div className="action-row">
          <button className="send-button" onClick={() => send()} disabled={!input.trim() || state === 'translating' || state === 'signing'}><Send /> <span>Traducir</span></button>
          <button className={`mic-button ${state === 'listening' ? 'active' : ''}`} onClick={() => state === 'listening' ? setState('idle') : setState('listening')} aria-label="Entrada por voz"><Mic /></button>
        </div>
        <div className="home-indicator" />
      </section>

      {historyOpen && <div className="overlay" onClick={() => setHistoryOpen(false)}>
        <section className="bottom-sheet" onClick={(event) => event.stopPropagation()} aria-label="Historial reciente">
          <div className="sheet-handle" /><div className="sheet-heading"><div><p className="eyebrow">TUS FRASES</p><h2>Historial reciente</h2></div><button className="icon-button small" onClick={() => setHistoryOpen(false)} aria-label="Cerrar"><X /></button></div>
          <div className="history-list">{history.map((phrase) => <button className="history-item" key={phrase} onClick={() => { setInput(phrase); setHistoryOpen(false); send(phrase) }}><span>{phrase}</span><span className="reuse">Reutilizar <ChevronRight /></span></button>)}</div>
        </section>
      </div>}

      {side && <div className="side-overlay" onClick={() => setSide(null)}>
        <aside className={`side-panel ${side === 'right' ? 'side-right' : 'side-left'}`} onClick={(event) => event.stopPropagation()}>
          <button className={`panel-edge-tab ${side === 'right' ? 'panel-edge-tab-right' : 'panel-edge-tab-left'}`} onClick={() => setSide(null)} aria-label="Cerrar panel"><span className="edge-grip" /></button>
          {side === 'left' ? <ConversationPanel text={lastText || input} gloss={glossFor(lastText || input)} status={state === 'done' ? 'Reproducción completada' : state === 'signing' ? 'Reproduciendo señas...' : 'Esperando traducción'} onRepeat={() => send(lastText || input)} close={() => setSide(null)} /> : <SettingsPanel character={character} setCharacter={setCharacter} dark={dark} setDark={setDark} speed={speed} setSpeed={setSpeed} bold={bold} setBold={setBold} contrast={contrast} setContrast={setContrast} large={large} setLarge={setLarge} reduceMotion={reduceMotion} setReduceMotion={setReduceMotion} openAbout={() => { setSide(null); setAbout(true) }} close={() => setSide(null)} />}
        </aside>
      </div>}
    </main>
  )
}

function ConversationPanel({ text, gloss, status, onRepeat, close }: { text: string; gloss: string; status: string; onRepeat: () => void; close: () => void }) {
  return <div className="panel-content"><div className="panel-top"><div><p className="eyebrow">CONTEXTO</p><h2>Conversación</h2></div><button className="icon-button small" onClick={close} aria-label="Cerrar"><X /></button></div><div className="conversation-block"><p className="label">TÚ</p><p className="quote">{text || 'Aún no has escrito una frase.'}</p></div><div className="conversation-block"><p className="label">TRADUCCIÓN</p><p className="gloss">{gloss}</p></div><div className="avatar-status"><div className="mini-avatar"><Sparkles /></div><div><p className="label">AVATAR</p><p>{status}</p></div></div><button className="repeat-icon-button" onClick={onRepeat} disabled={!text.trim()} aria-label="Repetir reproducción" title="Repetir reproducción"><RotateCcw /></button><div className="swipe-note">Desliza desde el borde para volver</div></div>
}

function SettingsPanel(props: { character: Character; setCharacter: (v: Character) => void; dark: boolean; setDark: (v: boolean) => void; speed: number; setSpeed: (v: number) => void; bold: boolean; setBold: (v: boolean) => void; contrast: boolean; setContrast: (v: boolean) => void; large: boolean; setLarge: (v: boolean) => void; reduceMotion: boolean; setReduceMotion: (v: boolean) => void; openAbout: () => void; close: () => void }) {
  const [characterOpen, setCharacterOpen] = useState(false)
  const characters: Character[] = ['Julián', 'Samuel', 'Ximena', 'Gris']
  return <div className="panel-content settings-content"><div className="panel-top"><div className="settings-logo">S</div><button className="icon-button small" onClick={props.close} aria-label="Cerrar"><X /></button></div><div className="setting-section"><p className="label">AVATAR</p><div className="character-select-row"><span className="setting-label"><span className="setting-icon"><Sparkles /></span><span>Personaje</span></span><div className="character-picker"><button className={`character-trigger ${characterOpen ? 'is-open' : ''}`} onClick={() => setCharacterOpen(!characterOpen)} aria-expanded={characterOpen} aria-haspopup="listbox"><span>{props.character}</span><ChevronDown aria-hidden="true" /></button>{characterOpen && <div className="character-menu" role="listbox" aria-label="Seleccionar personaje">{characters.map((name) => <button key={name} className={name === props.character ? 'selected' : ''} role="option" aria-selected={name === props.character} onClick={() => { props.setCharacter(name); setCharacterOpen(false) }}><span className={`character-dot dot-${name.toLowerCase()}`} />{name}{name === props.character && <Check aria-hidden="true" />}</button>)}</div>}</div></div><div className="range-row"><div><Volume2 /><span>Velocidad de señas</span></div><input type="range" min="10" max="100" value={props.speed} onChange={(e) => props.setSpeed(Number(e.target.value))} /></div></div><div className="setting-section"><p className="label">ACCESIBILIDAD</p><SettingRow icon={<Type />} label="Tamaño de interfaz"><input className="mini-range" type="range" defaultValue="50" /></SettingRow><SettingRow icon={<Type />} label="Texto en negrita"><Switch checked={props.bold} onChange={() => props.setBold(!props.bold)} /></SettingRow><SettingRow icon={<Contrast />} label="Contraste aumentado"><Switch checked={props.contrast} onChange={() => props.setContrast(!props.contrast)} /></SettingRow><SettingRow icon={<Plus />} label="Texto más grande"><Switch checked={props.large} onChange={() => props.setLarge(!props.large)} /></SettingRow><SettingRow icon={<Minus />} label="Reducir animaciones"><Switch checked={props.reduceMotion} onChange={() => props.setReduceMotion(!props.reduceMotion)} /></SettingRow></div><div className="setting-section"><p className="label">APARIENCIA</p><SettingRow icon={props.dark ? <Moon /> : <Sun />} label={props.dark ? 'Modo oscuro' : 'Modo claro'}><Switch checked={props.dark} onChange={() => props.setDark(!props.dark)} /></SettingRow></div><button className="about-link" onClick={props.openAbout}><Info /> Acerca de <ChevronRight /></button></div>
}

function SettingRow({ icon, label, children }: { icon: React.ReactNode; label: string; children: React.ReactNode }) { return <div className="setting-row"><span className="setting-label"><span className="setting-icon">{icon}</span>{label}</span>{children}</div> }
function Switch({ checked, onChange }: { checked: boolean; onChange: () => void }) { return <button className={`switch ${checked ? 'on' : ''}`} onClick={onChange} aria-pressed={checked} aria-label="Activar o desactivar"><span /></button> }
