export interface ApiErrorBody { timestamp?: string; status?: number; code?: string; message?: string; path?: string; traceId?: string; fieldErrors?: { field: string; message: string }[] }
export class ApiError extends Error { status: number; code: string; fieldErrors: {field:string;message:string}[]; traceId?: string;
  constructor(status:number, body: ApiErrorBody, fallback='Request failed'){ super(body.message||fallback); this.status=status; this.code=body.code||`HTTP_${status}`; this.fieldErrors=body.fieldErrors||[]; this.traceId=body.traceId; } }
export interface PageResponse<T> { items: T[]; nextCursor?: string|null; hasMore?: boolean }
export interface Post { id: string|number; title?: string; body?: string; content?: string; authorId?: number; authorName?: string; author?: {fullName?:string}; level?: string; sector?: string; sectors?: string[]; createdAt?: string; reactionCount?: number; commentCount?: number; visibility?: string; status?: string; eventDeadline?: string; regLink?: string; location?: string }
export interface EventItem { id: string|number; title: string; type?: string; deadline?: string; eventDeadline?: string; date?: string; visibility?: string; description?: string; body?: string; regLink?: string; location?: string; status?: string; authorId?: number }
export interface UserProfile { id: string|number; userId?: number; fullName: string; email?: string; university?: string; department?: string; batch?: string; verified?: boolean; bio?: string; skills?: string[]; phone?: string; state?: string; headline?: string; links?: string; sectors?: string[]; followers?: number; following?: number; isOwner?: boolean; universityName?: string; departmentName?: string; batchLabel?: string }
export interface Room { id: string|number; name: string; type: string; lastMessage?: string }
export interface NotificationItem { id: string|number; title: string; body?: string; message?: string; createdAt?: string; read?: boolean; readFlag?: boolean; type?: string }
export interface VerificationItem { id: string|number; fullName: string; email: string; university?: string; studentId?: string; status?: string; submittedAt?: string; ocrMatchScore?: number; ocrText?: string; previousAttempts?: number; duplicatePhone?: boolean }
export const SECTORS = ['Software Engineering','Data Science','AI/ML','Cyber Security','Web Development','Competitive Programming'];
export const LEVELS = ['DEPARTMENT','UNIVERSITY','HUB','NATIONAL'] as const;

// --- PHASE 2 NON-AUTH TYPES (pages-and-endpoints worker, I-7/I-8/I-9) ---
// Anchor: this block is the non-auth section. The auth worker owns the
// AUTH TYPES block below; keep non-auth additions inside this block.
export interface SectorTag { id: number; name: string; active?: boolean }
export interface AttachmentItem { id: string|number; fileName: string; contentType?: string; sizeBytes?: number; createdAt?: string }
export interface CommunityContext { universityId?: number; universityName?: string; departmentId?: number; departmentName?: string; catalogId?: number; departmentRoomPublicId?: string; universityRoomPublicId?: string }
/** Notification types the backend actually emits (NotificationEventListener). */
export const NOTIFICATION_TYPES = ['VERIFICATION_DECISION','REPORT_AUTO_HIDE','TEAM_JOIN_ACCEPTED'] as const;
export interface ProjectItem { id: string|number; title?: string; description?: string; url?: string }
export interface EducationItem { id: string|number; institution?: string; degree?: string; year?: number }
export interface AchievementItem { id: string|number; title?: string; description?: string; year?: number }
// --- END PHASE 2 NON-AUTH TYPES ---

// --- AUTH TYPES (auth worker, I-4/I-5/I-6/I-10) ---
// Anchor: everything below this line is the auth section. Another worker owns
// the non-auth types above; keep auth additions inside this block.
export interface CatalogUniversity { id: number; name: string; shortName?: string; category?: string; active?: boolean }
export interface CatalogDepartment { id: number; universityId: number; catalogId?: number; name: string; active?: boolean }
export interface CatalogBatch { id: number; universityId: number; departmentId?: number; label: string; startYear?: number; active?: boolean }
/** Bangladeshi mobile: 11 digits, 01[3-9]xxxxxxxx. Must match the backend
 *  @Pattern on RegisterForm.phone (FR-AUTH-02). */
export const BD_PHONE_RE = /^01[3-9]\d{8}$/;
export const BD_PHONE_MESSAGE = 'Enter a valid Bangladeshi mobile number, e.g. 01712345678';
// --- END AUTH TYPES ---
