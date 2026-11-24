import { Link } from 'react-router-dom';
import { PageHeader, StatStrip } from '../../design/primitives';
// No Tailwind in this project (plain tokens.css), so the requested
// text-4xl sm:text-5xl lg:text-6xl font-bold tracking-tight
// leading-tight headline and pt-24 md:pt-32 pb-16 hero spacing are
// expressed here in the same units: 36px / 48px / 60px, weight 700,
// -0.025em tracking, 1.25 leading; 96px top padding, 128px on desktop.
const LANDING_CSS = `
.landing-hero{padding-top:96px;padding-bottom:64px}
.landing-hero .display{font-size:2.25rem;font-weight:700;letter-spacing:-0.025em;line-height:1.25}
@media (min-width:640px){.landing-hero .display{font-size:3rem}}
@media (min-width:768px){.landing-hero{padding-top:128px}}
@media (min-width:1024px){.landing-hero .display{font-size:3.75rem}}
`;
export default function Landing(){ return <div className="container landing-hero"><style>{LANDING_CSS}</style><PageHeader eyebrow="FOR UNIVERSITY STUDENTS IN BANGLADESH" title="Where university students connect, collaborate, and build together" lead="Join a trusted academic network exclusive to verified students. Discuss coursework, discover campus events, and find teammates for your next project or hackathon." action={<><Link className="btn btn-primary" to="/signup">Get started →</Link>{' '}<Link className="btn" to="/login">Log in</Link></>} />
<StatStrip stats={[{label:'CAMPUS COMMUNITIES',value:'Your Department & Beyond',note:'Engage in focused discussions across your department, university, and nationwide student hubs.'},{label:'TEAM & PROJECT DISCOVERY',value:'Build With Real Peers',note:'Find skilled teammates for hackathons, research, and software projects across campuses.'},{label:'AUTHENTIC NETWORK',value:'Verified Students Only',note:'Every profile is backed by university ID verification—zero spam, zero outsiders.'}]} />
</div>; }
