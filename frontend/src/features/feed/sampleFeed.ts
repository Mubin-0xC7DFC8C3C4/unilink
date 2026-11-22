/**
 * Prototype Preview content for Community Home (Phase 3).
 *
 * This module is the ONLY source of sample posts in the app, kept fully
 * isolated from the real feed. Feed.tsx renders these posts solely when
 * the live feed request fails (backend unavailable), always under the
 * single "Prototype Preview" banner, and every interaction on them is
 * local to the browser — nothing here is sent to, or read from, an API.
 * When the backend is reachable, this data is never shown.
 */
export interface SampleComment { authorName: string; body: string; meta?: string; timeLabel?: string }
export interface SamplePost {
  id: string;
  contextLine?: string;
  media?: boolean;
  authorName: string;
  authorMeta: string;
  timeLabel: string;
  scopeLabel: string;
  title: string;
  body: string;
  sectors: string[];
  reactionCount: number;
  comments: SampleComment[];
}

export interface SampleStudent { name: string; meta: string }

/** Suggested students shown only in Prototype Preview (there is no
 *  suggestions endpoint in the backend); in real mode the card is not
 *  rendered at all rather than presenting invented people. */
export const SAMPLE_SUGGESTED: SampleStudent[] = [
  { name: 'Sadia Islam', meta: 'CSE · Batch 28' },
  { name: 'Rakib Ahmed', meta: 'CSE · Batch 27' },
];

export const SAMPLE_POSTS: SamplePost[] = [
  {
    id: 'sample-1',
    authorName: 'Farhana Islam',
    authorMeta: 'CSE · Metropolitan University · Batch 27',
    timeLabel: '2 hours ago',
    scopeLabel: 'Department',
    title: 'Final-year project update: real-time campus bus tracking',
    body: 'Our team just finished the live map view for the campus bus tracker. Drivers can share their route from a phone, and students see the next bus on the university gate board. We are looking for feedback on the driver app screens before the mid-review — especially from anyone who has built map interfaces before.',
    sectors: ['Software Engineering', 'Web Development'],
    reactionCount: 24,
    comments: [
      { authorName: 'Tanvir Hasan', body: 'The gate board view is a great touch. How are you handling GPS gaps between stops?', meta: 'CSE · Batch 27', timeLabel: '1h' },
      { authorName: 'Farhana Islam', body: 'We interpolate between the last two pings and mark the position as estimated. Happy to share the approach at the review.', meta: 'CSE · Batch 27', timeLabel: '32m' },
    ],
  },
  {
    id: 'sample-2',
    authorName: 'Tanvir Hasan',
    authorMeta: 'CSE · Metropolitan University · Batch 27',
    timeLabel: '5 hours ago',
    scopeLabel: 'Department',
    title: 'Forming a team for the inter-university programming contest',
    body: 'We have two confirmed members and are looking for a third who is comfortable with graph problems and dynamic programming. Practice sessions run every Saturday morning in the CSE lab. Reply here if you want to join the next practice set.',
    sectors: ['Competitive Programming'],
    reactionCount: 17,
    comments: [
      { authorName: 'Nusrat Jahan', body: 'I can join for the graph topics. Sending you my handle from the last contest.', meta: 'CSE · Batch 28', timeLabel: '3h' },
    ],
  },
  {
    id: 'sample-3',
    contextLine: 'Tanvir Hasan reposted this',
    authorName: 'Nusrat Jahan',
    authorMeta: 'CSE · Metropolitan University · Batch 27',
    timeLabel: 'Yesterday',
    scopeLabel: 'Department',
    title: 'Data Structures study group for the final exams',
    body: 'Starting a small study group for the final exams — trees and hashing this week, then sorting and graph traversals. We meet in the central library, third floor, at 4 pm on Sundays and Wednesdays. Bring your problem sheets; we solve first, discuss after.',
    sectors: [],
    reactionCount: 31,
    comments: [
      { authorName: 'Arif Chowdhury', body: 'Count me in for Wednesday. I will bring the past papers from the last two terms.', meta: 'EEE · Batch 26', timeLabel: '5h' },
    ],
  },
  {
    id: 'sample-4',
    media: true,
    authorName: 'Arif Chowdhury',
    authorMeta: 'EEE · Metropolitan University · Batch 26',
    timeLabel: 'Yesterday',
    scopeLabel: 'Department',
    title: 'Photography Society: winter campus walk this Friday',
    body: 'The Photography Society is organising a winter campus walk this Friday after Jummah. All departments are welcome — bring a phone or a camera, whatever you have. The best shots will be exhibited in the student lounge next month.',
    sectors: [],
    reactionCount: 42,
    comments: [],
  },
];
