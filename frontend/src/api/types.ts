export interface ApiErrorBody { timestamp?: string; status?: number; code?: string; message?: string; path?: string; traceId?: string; fieldErrors?: { field: string; message: string }[] }
export class ApiError extends Error { status: number; code: string; fieldErrors: {field:string;message:string}[]; traceId?: string;
  constructor(status:number, body: ApiErrorBody, fallback='Request failed'){ super(body.message||fallback); this.status=status; this.code=body.code||`HTTP_${status}`; this.fieldErrors=body.fieldErrors||[]; this.traceId=body.traceId; } }
export interface PageResponse<T> { items: T[]; nextCursor?: string|null; hasMore?: boolean }
export interface Post { id: string|number; title?: string; body?: string; content?: string; authorName?: string; author?: {fullName?:string}; level?: string; sector?: string; createdAt?: string; reactionCount?: number; commentCount?: number; visibility?: string }
export interface EventItem { id: string|number; title: string; type?: string; deadline?: string; date?: string; visibility?: string; description?: string }
export interface UserProfile { id: string|number; fullName: string; email?: string; university?: string; department?: string; batch?: string; verified?: boolean; bio?: string; skills?: string[] }
export interface Room { id: string|number; name: string; type: string; lastMessage?: string }
export interface NotificationItem { id: string|number; title: string; body?: string; message?: string; createdAt?: string; read?: boolean }
export interface VerificationItem { id: string|number; fullName: string; email: string; university?: string; studentId?: string; status?: string; submittedAt?: string; ocrMatchScore?: number; ocrText?: string; previousAttempts?: number }
export const SECTORS = ['Software Engineering','Data Science','AI/ML','Cyber Security','Web Development','Competitive Programming'];
export const LEVELS = ['DEPARTMENT','UNIVERSITY','HUB','NATIONAL'] as const;
