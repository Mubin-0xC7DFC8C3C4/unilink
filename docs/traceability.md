# SRS Traceability Matrix (template, pre-filled with the planned mapping)

Purpose: one row per SRS requirement (116 requirements: FR and NFR) and one row per SRS test case (TC-01 to TC-31). The **planned** columns are filled from the design in `ARCHITECTURE_OVERVIEW.md` and `REQUIREMENTS_DECISION_LOG.md`. The implementation agent fills **Implementation location** with real file paths, **Executed result** with real test output, and **Status** only from evidence.

## Status vocabulary (use exactly these)
`Not started` | `In progress` | `Completed` (implemented and its tests executed and passing) | `Partially completed` (say what is missing) | `Not implemented` (give reason or decision id) | `Blocked by external dependency` (name it) | `Not tested` (implemented, no executed passing test)

A requirement may be `Completed` only if every mapped test was actually executed and passed, and the date and command are in the **Evidence** column. Never copy a "pass" from another row.

## Test ID scheme
`UT-` unit, `IT-` integration (Spring Boot Test, Testcontainers, MockMvc/REST Assured), `E2E-` Playwright, `SEC-` security test, `PERF-` load test (k6), `UAT-` usability or acceptance session, `OPS-` operational drill, `REV-` documented review. `TC-nn` are the SRS acceptance test cases. Test IDs below are planned names; create tests with exactly these IDs in the test display name or a tag so they can be grepped.

## Columns
Requirement ID | Requirement (SRS text, abridged) | Frontend | Backend endpoint or job | Service / pattern | Entities | Authorisation and validation | Planned tests | Implementation location | Status | Evidence (command, date, result)



## Authentication and Verification

| ID | Requirement | Frontend | Backend | Service / pattern | Entities | Authz and validation | Planned tests | Implementation location | Status | Evidence |
|---|---|---|---|---|---|---|---|---|---|---|
| FR-AUTH-01 | The system shall provide a sign-up form collecting: full name, phone number, email, university, department, ba... | SignUpPage (multi-step form) | POST /api/auth/register (multipart) | RegistrationService; StorageGateway | users, user_credentials, profiles, verification_requests, attachments | Visitor only; CSRF; field validation per FR-AUTH-02 | TC-01, IT-AUTH-01, E2E-AUTH-01 | TBD | Not started | |
| FR-AUTH-02 | The system shall validate all inputs: required fields, email format, mobile number format, unique email and un... | Form validation (client hints only) | POST /api/auth/register | Bean Validation + ContentValidator chain (image type and size) | users (unique email), (university_id, student_id) unique | Server is authoritative; 409 on duplicates | TC-02, UT-AUTH-02, IT-AUTH-02 | TBD | Not started | |
| FR-AUTH-03 | The system shall require a password of at least 8 characters containing upper-case, lower-case and a digit and... | Password field with rules | POST /api/auth/register | PasswordPolicyValidator; Argon2id DelegatingPasswordEncoder | user_credentials | 8+ chars, upper, lower, digit | UT-AUTH-03, SEC-AUTH-03 | TBD | Not started | |
| FR-AUTH-04 | The system shall send an OTP to the student's phone or email after sign-up; the OTP shall expire in 5 minutes ... | OtpStep | POST /api/auth/otp/send, /otp/verify | OtpService; SmsProvider strategy with email fallback | phone_otps (hashed code, attempts, expires_at) | 5 min expiry, 5 attempts, resend cooldown, rate limits | TC-03, UT-AUTH-04, IT-AUTH-04, SEC-AUTH-04 | TBD | Not started | |
| FR-AUTH-05 | The system shall send an email verification link and mark the email as verified when it is opened. | VerifyEmailPage | GET /api/auth/email/verify?token= | EmailVerificationService; EmailProvider adapter | email_verification_tokens, users.email_verified_at | Hashed single-use token, 24 h expiry | IT-AUTH-05, E2E-AUTH-01 | TBD | Not started | |
| FR-AUTH-06 | The system shall run OCR on the uploaded ID card and compare the extracted student ID number and university na... | Admin review OCR panel | Async OcrJob (no public endpoint) | OcrProvider strategy; ocr result compare | verification_attempts (ocr_text, match_score) | OCR failure never blocks queue (NFR-FLT-03) | TC-04, UT-AUTH-06, IT-AUTH-06 | TBD | Not started | |
| FR-AUTH-07 | After OTP and email verification the account shall enter Pending Review and appear in the admin verification q... | StatusBanner on student app | Internal transition after OTP+email | VerificationFacade; AccountState machine | users.status, verification_requests | Only reviewers approve; scope by university | TC-01, UT-AUTH-07, IT-AUTH-07 | TBD | Not started | |
| FR-AUTH-08 | If rejected, the student shall see the rejection reason and be able to resubmit up to 3 times in 24 hours. | RejectedNotice + ResubmitForm | POST /api/verification/resubmit | VerificationFacade; rolling 24h limiter | verification_attempts | Max 3 resubmissions per rolling 24h | TC-04, UT-AUTH-08, IT-AUTH-08 | TBD | Not started | |
| FR-AUTH-09 | The system shall notify the student by email and in-app of the approval or rejection decision. | Notification center, email | Event StudentVerified/StudentRejected | NotificationService via Observer; DeliveryChannelFactory | notifications | Not optional email (D-029c) | TC-01, IT-AUTH-09 | TBD | Not started | |
| FR-AUTH-10 | The system shall allow login with email and password and shall lock login for 15 minutes after 5 consecutive f... | LoginPage | POST /api/auth/login | LoginService; LoginThrottle (lock 15 min after 5 failures) | user_credentials.failed_attempts, locked_until | Generic error text; no user enumeration | TC-05, UT-AUTH-10, SEC-AUTH-10 | TBD | Not started | |
| FR-AUTH-11 | The system shall provide Forgot Password: request an OTP, verify it, and set a new password. | ForgotPassword flow | POST /api/auth/password/forgot, /verify-otp, /reset | PasswordResetService; invalidates all sessions | password_reset_tokens, phone_otps | Rate-limited; generic responses | TC-06, IT-AUTH-11, SEC-AUTH-11 | TBD | Not started | |
| FR-AUTH-12 | The system shall provide logout and shall end sessions after 2 hours of inactivity. | Logout action; idle-timeout dialog | POST /api/auth/logout; session idle 2 h | Spring Session JDBC | spring_session | HttpOnly, Secure, SameSite=Lax cookie | IT-AUTH-12, SEC-AUTH-12 | TBD | Not started | |
| FR-AUTH-13 | Until verification succeeds, the system shall restrict the account to limited access. | Locked-feature prompts | All write endpoints | AccessPolicy.requireVerified() | users.status | Server-side on every write | TC-07, IT-AUTH-13, SEC-AUTH-13 | TBD | Not started | |

## Student Profile

| ID | Requirement | Frontend | Backend | Service / pattern | Entities | Authz and validation | Planned tests | Implementation location | Status | Evidence |
|---|---|---|---|---|---|---|---|---|---|---|
| FR-PROF-01 | The system shall allow a student to upload and change a profile picture and a cover photo. | ProfilePhotoUploader | PUT /api/me/profile/photo, /cover | ProfileService; StorageGateway; image sanitiser | profiles, attachments | Owner only; 2 MB / 5 MB, type sniffing | IT-PROF-01 | TBD | Not started | |
| FR-PROF-02 | The system shall show university, department and batch (taken from verified sign-up data) and let the student ... | ProfileEditor | PATCH /api/me/profile | ProfileService | profiles, user_skills, user_sector_follows | University/department/batch read-only (FR-PROF-09) | IT-PROF-02 | TBD | Not started | |
| FR-PROF-03 | The system shall allow adding, editing and deleting projects, each with title, description, tech stack, GitHub... | ProjectList / ProjectForm | CRUD /api/me/projects | ProjectService | projects | Owner only (IDOR test) | TC-08, IT-PROF-03, SEC-PROF-03 | TBD | Not started | |
| FR-PROF-04 | The system shall allow adding education entries and achievements. | EducationForm, AchievementForm | CRUD /api/me/education, /achievements | ProfileService | education_entries, achievements | Owner only | IT-PROF-04 | TBD | Not started | |
| FR-PROF-05 | The system shall allow adding external links: GitHub, LinkedIn, Portfolio, Codeforces, LeetCode and others. | LinksEditor | PATCH /api/me/profile/links | ProfileService; URL validation allow-list | profiles.links | http(s) only; no javascript: URLs | UT-PROF-05, SEC-PROF-05 | TBD | Not started | |
| FR-PROF-06 | The system shall provide Follow / Connect on every profile and show follower and connection counts. | FollowButton, counts | POST/DELETE /api/users/{id}/follow | FollowService | follows | Verified; not self | IT-PROF-06 | TBD | Not started | |
| FR-PROF-07 | The system shall show an activity feed on the profile (posts, events joined, projects added). | ActivityFeed on profile | GET /api/users/{id}/activity | ActivityQueryService (Specification) | posts, event_responses, projects | Respects viewer visibility scope | IT-PROF-07 | TBD | Not started | |
| FR-PROF-08 | Phone number and address shall be hidden from other users by default; the owner may choose to show the phone n... | Privacy toggle | PATCH /api/me/privacy; GET profile DTO | ProfileDtoMapper hides phone/address | profiles.show_phone | Address never in other-user DTO | UT-PROF-08, SEC-PROF-08 | TBD | Not started | |
| FR-PROF-09 | Changing university, department or student ID shall require repeating verification (FR-AUTH-06). | IdentityChangeForm | POST /api/me/identity-change | IdentityChangeService (D-017) | identity_change_requests | Re-verification; old values stay until approval | UT-PROF-09, IT-PROF-09 | TBD | Not started | |

## Community

| ID | Requirement | Frontend | Backend | Service / pattern | Entities | Authz and validation | Planned tests | Implementation location | Status | Evidence |
|---|---|---|---|---|---|---|---|---|---|---|
| FR-COM-01 | The system shall place a verified student automatically in a spaces derived from the profile. | Community shell | GET /api/community/context | CommunityScopeService | users, departments, department_catalog | Derived from verified profile only | UT-COM-01 | TBD | Not started | |
| FR-COM-02 | Department section shall show only students and posts of the same university and the same department. | DepartmentFeed | GET /api/feed?level=DEPARTMENT | FeedSpecifications (Specification) | posts | Same university and department | TC-09, IT-COM-02, SEC-COM-02 | TBD | Not started | |
| FR-COM-03 | University section shall show all students and posts of the same university across all departments and batches... | UniversityFeed | GET /api/feed?level=UNIVERSITY | FeedSpecifications | posts | Same university only (D-021) | IT-COM-03, SEC-COM-03 | TBD | Not started | |
| FR-COM-04 | National Department Hub section shall show students and posts of the same department from every university. | HubFeed | GET /api/feed?level=HUB | FeedSpecifications on catalog_id | posts, department_catalog | Same catalog department, any university (D-020) | TC-10, IT-COM-04 | TBD | Not started | |
| FR-COM-05 | Feed shall show all visibility events, posts and discussions. | NationalFeed | GET /api/feed?level=NATIONAL | FeedSpecifications | posts | NATIONAL visibility; unverified read-only (D-016) | TC-11, IT-COM-05 | TBD | Not started | |
| FR-COM-06 | Messaging and collaboration shall be available at every community level. | Start-chat entry points | POST /api/rooms/private; team rooms | MessagingService (D-022) | chat_rooms | Verified, blocks respected | IT-COM-06 | TBD | Not started | |
| FR-COM-07 | After login the system shall open the Department section feed by default and allow switching levels with a sin... | Default route to Department tab; level switcher | GET /api/community/context | Frontend router default | n/a | n/a | E2E-COM-07 | TBD | Not started | |

## Messaging

| ID | Requirement | Frontend | Backend | Service / pattern | Entities | Authz and validation | Planned tests | Implementation location | Status | Evidence |
|---|---|---|---|---|---|---|---|---|---|---|
| FR-MSG-01 | The system shall provide a group chat for each of: Department, University. | RoomList (Department, University) | Auto rooms created by CommunityRoomService | MessagingService | chat_rooms, room_members | Member of that department / university only | IT-MSG-01, SEC-MSG-01 | TBD | Not started | |
| FR-MSG-02 | The system shall provide private one-to-one chat between verified students. | PrivateChat | POST /api/rooms/private; STOMP /app/chat.send | MessagingService | chat_rooms, messages | Both verified; not blocked | TC-15, IT-MSG-02 | TBD | Not started | |
| FR-MSG-03 | The system shall provide a search bar inside messages to find a conversation or person by name. | MessageSearchBar | GET /api/rooms/search?q= | SearchStrategy (names only, D-029) | chat_rooms, users | Only own rooms and permitted people | TC-16, IT-MSG-03 | TBD | Not started | |
| FR-MSG-04 | The system shall allow a student to create a temporary group chat for team formation and invite other students... | NewGroupDialog | POST /api/rooms/temp; POST /api/rooms/{id}/invite | MessagingService | chat_rooms(TEMP_GROUP), room_members | Creator becomes room admin | IT-MSG-04 | TBD | Not started | |
| FR-MSG-05 | Messages shall be delivered in real time, stored permanently and shown with time and read status. | ChatWindow with ticks | STOMP /app/chat.send, /topic/room.{id}; POST /api/rooms/{id}/read | MessagingService; RealtimeBroadcaster (after commit) | messages, room_members.last_read_message_id | Authorised SUBSCRIBE and SEND | TC-15, IT-MSG-05, PERF-MSG-05 | TBD | Not started | |
| FR-MSG-06 | The system shall allow students to block a user and mute a conversation. | Block / Mute menu | POST /api/users/{id}/block; PUT /api/rooms/{id}/mute | BlockService | user_blocks, room_members.muted_until | Owner only | IT-MSG-06 | TBD | Not started | |
| FR-MSG-07 | The system should allow sending images and files up to a specific limit. | Attach button | POST /api/rooms/{id}/attachments | AttachmentService; ContentValidator chain | message_attachments, attachments | 10 MB, sniffed types | IT-MSG-07, SEC-MSG-07 | TBD | Not started | |
| FR-MSG-08 | The system shall allow group admin to remove members and close temporary chats. | Room admin actions | DELETE /api/rooms/{id}/members/{uid}; POST /api/rooms/{id}/close | MessagingService | room_members, chat_rooms | Room admin only | IT-MSG-08, SEC-MSG-08 | TBD | Not started | |

## Events

| ID | Requirement | Frontend | Backend | Service / pattern | Entities | Authz and validation | Planned tests | Implementation location | Status | Evidence |
|---|---|---|---|---|---|---|---|---|---|---|
| FR-EVT-01 | The system shall allow a verified student to create an event post of type Seminar, Workshop, Competition, Hack... | EventComposer | POST /api/events | EventService; ContentValidator chain | posts, event_details | Verified; type enum | TC-11, IT-EVT-01 | TBD | Not started | |
| FR-EVT-02 | Each event post shall support visibility options. | Visibility selector | POST /api/events | VisibilityResolver (D-021) | posts.visibility, scope_* | Visibility must be reachable from author scope | TC-11, UT-EVT-02 | TBD | Not started | |
| FR-EVT-03 | The event detail page shall show full description, poster, registration link, deadline, prizes, eligibility an... | EventDetailPage | GET /api/events/{id} | EventQueryService | event_details, attachments | Viewer must be in scope | IT-EVT-03 | TBD | Not started | |
| FR-EVT-04 | The system shall provide a comment section on every event and allow tagging other students with @name. | CommentThread with @mention | POST /api/posts/{id}/comments | CommentService; MentionResolver | comments, comment_mentions | Verified; mentioned user must be in audience | IT-EVT-04, SEC-EVT-04 | TBD | Not started | |
| FR-EVT-05 | A scheduled job shall automatically move an event to Past Events when its deadline passes; it shall be removed... | Past Events archive tab | Job EventArchiveJob (every minute) | EventLifecycleService (State); also deadline > now() filter | event_details.archived_at | Idempotent, retried | TC-12, UT-EVT-05, IT-EVT-05 | TBD | Not started | |
| FR-EVT-06 | Competition and hackathon posts shall have a Looking for Teammates settings. | Looking for Teammates toggle | POST /api/events/{id}/team-request | TeamFormationFacade | teams | Only Competition/Hackathon | IT-EVT-06 | TBD | Not started | |
| FR-EVT-07 | The sidebar shall list Upcoming Events sorted by nearest deadline. | UpcomingSidebar | GET /api/events/upcoming | EventQueryService | event_details | Scope-filtered, nearest deadline first | TC-11, IT-EVT-07 | TBD | Not started | |
| FR-EVT-08 | The event owner shall be able to edit or delete own event; edits to the deadline shall re-evaluate its active/... | Edit/Delete event | PATCH/DELETE /api/events/{id} | EventService | posts, event_details | Owner only | IT-EVT-08, SEC-EVT-08 | TBD | Not started | |
| FR-EVT-09 | The system shall reject an event whose deadline is in the past at creation time. | Deadline field validation | POST /api/events | FutureDeadlineValidator (Clock) | event_details | Past deadline rejected | TC-13, UT-EVT-09 | TBD | Not started | |

## Content

| ID | Requirement | Frontend | Backend | Service / pattern | Entities | Authz and validation | Planned tests | Implementation location | Status | Evidence |
|---|---|---|---|---|---|---|---|---|---|---|
| FR-CNT-01 | The system shall allow study posts, project idea posts and general discussion posts. | PostComposer | POST /api/posts | PostService | posts | Verified | IT-CNT-01 | TBD | Not started | |
| FR-CNT-02 | The system shall allow comments, reactions and editing or deleting of own posts. | Comments, reactions, edit/delete | /api/posts/{id}/comments, /reactions | PostService, ReactionService | comments, reactions | Owner for edit/delete | IT-CNT-02, SEC-CNT-02 | TBD | Not started | |
| FR-CNT-03 | The system should allow attaching images or PDF files to a post. | Attachment picker | POST /api/posts/{id}/attachments | AttachmentService | attachments | Images/PDF, 10 MB | IT-CNT-03, SEC-CNT-03 | TBD | Not started | |

## Search

| ID | Requirement | Frontend | Backend | Service / pattern | Entities | Authz and validation | Planned tests | Implementation location | Status | Evidence |
|---|---|---|---|---|---|---|---|---|---|---|
| FR-SRC-01 | The system shall provide a search bar to find things easily. | GlobalSearchBar | GET /api/search?q=&type= | SearchStrategy (FULLTEXT) | posts, users | Results filtered by viewer scope | TC-17, IT-SRC-01, PERF-SRC-01 | TBD | Not started | |
| FR-SRC-02 | The system shall allow searching and filtering by topic / sector. | Sector filter chips | GET /api/search, /api/feed?sector= | SearchSpecifications | post_sector_tags, sector_tags | Scope-filtered | TC-17, IT-SRC-02 | TBD | Not started | |

## Sectors

| ID | Requirement | Frontend | Backend | Service / pattern | Entities | Authz and validation | Planned tests | Implementation location | Status | Evidence |
|---|---|---|---|---|---|---|---|---|---|---|
| FR-SEC-01 | The system shall support sectors including Software Engineering, Data Science, AI/ML, Cyber Security, Web Deve... | Sector picker | GET /api/sectors | SectorService | sector_tags | Master data by admin | TC-29, IT-SEC-01 | TBD | Not started | |
| FR-SEC-02 | Students shall be able to follow sectors and filter every feed by sector. | Follow sector, feed filter | PUT /api/me/sectors | SectorService | user_sector_follows | Verified | IT-SEC-02 | TBD | Not started | |

## Teams

| ID | Requirement | Frontend | Backend | Service / pattern | Entities | Authz and validation | Planned tests | Implementation location | Status | Evidence |
|---|---|---|---|---|---|---|---|---|---|---|
| FR-TEAM-01 | Looking for Teammates shall create a team request linked to the event with required skills and team size. | TeamRequestForm | POST /api/events/{id}/team-request | TeamFormationFacade | teams | Verified; event active | TC-14, IT-TEAM-01 | TBD | Not started | |
| FR-TEAM-02 | Other students shall be able to send a join request; the team creator shall accept or decline. | JoinRequest / Accept-Decline | POST /api/teams/{id}/join; POST /api/teams/{id}/requests/{rid}/accept|decline | TeamService | team_join_requests | Creator only decides | TC-14, IT-TEAM-02, SEC-TEAM-02 | TBD | Not started | |
| FR-TEAM-03 | On acceptance the system shall add the student to the team and to a temporary team group chat. | Team chat | On accept: add member + room member (one transaction) | TeamFormationFacade | team_members, room_members | Atomic | TC-14, IT-TEAM-03 | TBD | Not started | |
| FR-TEAM-04 | A team shall close automatically when its size limit is reached or the event becomes a Past Event. | Team closed banner | Auto on full, or EventArchiveJob | TeamState machine | teams.status | Idempotent | UT-TEAM-04, IT-TEAM-04 | TBD | Not started | |

## Calendar, Notifications, Bookmarks

| ID | Requirement | Frontend | Backend | Service / pattern | Entities | Authz and validation | Planned tests | Implementation location | Status | Evidence |
|---|---|---|---|---|---|---|---|---|---|---|
| FR-CAL-01 | The system shall provide a calendar view showing events by deadline. | CalendarPage | GET /api/calendar?month= | CalendarService | event_details | Scope-filtered; bookmarked filter | IT-CAL-01 | TBD | Not started | |
| FR-NOT-01 | Students shall be able to choose which notification types also arrive by email. | NotificationPreferences | PUT /api/me/notification-preferences | NotificationPreferenceService | notification_preferences | Security emails cannot be disabled | IT-NOT-01 | TBD | Not started | |
| FR-BMK-01 | The system shall allow saving and removing bookmarks on events and posts and show them in a Saved page. | SavedPage, bookmark toggle | PUT/DELETE /api/bookmarks | BookmarkService | bookmarks | Verified; target visible to user | IT-BMK-01 | TBD | Not started | |

## Reporting

| ID | Requirement | Frontend | Backend | Service / pattern | Entities | Authz and validation | Planned tests | Implementation location | Status | Evidence |
|---|---|---|---|---|---|---|---|---|---|---|
| FR-REP-01 | Every section have a Report option with a reason (spam, fake event, abuse, other). | ReportDialog | POST /api/reports | ReportService; ReportEligibilityHandler chain | reports | Verified, not owner, unique | IT-REP-01 | TBD | Not started | |
| FR-REP-02 | When an item receives reports from 5 different verified students, the system shall auto-hide it from feeds, no... | Hidden notice to owner | Triggered in ReportService | ModerationPolicy; PostState; AFTER_COMMIT events | reports, posts.status, moderation_events | Distinct verified reporters; threshold setting | TC-18, UT-REP-02, IT-REP-02 | TBD | Not started | |
| FR-REP-03 | The owner shall be able to appeal a hidden item; the appeal appears in the admin queue and the admin decides. | AppealForm | POST /api/appeals | AppealService | appeals | Owner only | TC-19, IT-REP-03 | TBD | Not started | |
| FR-REP-04 | A student whose content is auto-hidden 3 times within 30 days shall be flagged for admin review and may be res... | Posting restriction notice | Strike check after auto-hide | StrikeService | moderation_events, users.posting_restricted_until | 3 in 30 days flags for review | UT-REP-04, IT-REP-04 | TBD | Not started | |

## Admin Portal

| ID | Requirement | Frontend | Backend | Service / pattern | Entities | Authz and validation | Planned tests | Implementation location | Status | Evidence |
|---|---|---|---|---|---|---|---|---|---|---|
| FR-ADM-01 | The system shall provide a separate Admin Portal login with email, password and mandatory two-factor (OTP) aut... | AdminLogin + TwoFactor | POST /api/admin/auth/login, /2fa/verify | AdminAuthService; TotpService | admin_accounts, admin_totp_secrets | Separate throttle; MFA_VERIFIED authority | TC-23, IT-ADM-01, SEC-ADM-01 | TBD | Not started | |
| FR-ADM-02 | The portal shall show a dashboard with counts of registered users, pending verifications, reports awaiting rev... | AdminDashboard (real counts only) | GET /api/admin/dashboard | AdminDashboardService | users, verification_requests, reports, posts | Scope: moderator sees own university | IT-ADM-02, SEC-ADM-02 | TBD | Not started | |
| FR-ADM-03 | The portal shall provide a verification queue listing pending students with entered details, ID card image, OC... | VerificationQueue + ReviewPane | GET /api/admin/verifications; POST .../{id}/approve|reject | VerificationReviewService (audited) | verification_requests, attempts | Reason mandatory on reject; scope by university | TC-24, TC-25, TC-04, IT-ADM-03, SEC-ADM-03 | TBD | Not started | |
| FR-ADM-04 | The admin shall be able to search and view users, suspend, ban or reactivate accounts, revoke verification and... | UserAdmin | GET/PATCH /api/admin/users; POST .../suspend|ban|reactivate|revoke|force-reset | UserAdminService (audited) | users, user sessions | Reason mandatory; moderator limited (D-036) | TC-28, IT-ADM-04, SEC-ADM-04 | TBD | Not started | |
| FR-ADM-05 | The portal shall provide a moderation queue of reported and auto-hidden posts, comments, messages and profiles... | ModerationQueue | GET /api/admin/moderation | ModerationQueueService | reports, posts, comments, moderation_snapshots | Ordered by report count then age; scoped | TC-18, IT-ADM-05 | TBD | Not started | |
| FR-ADM-06 | The admin shall be able to hide, remove, restore or edit-request any post, event or comment and send a warning... | ModerationActions | POST /api/admin/content/{type}/{id}/hide|remove|restore|request-edit|warn | ModerationFacade; PostState | posts, comments, notifications | Scoped; audited | TC-19, IT-ADM-06 | TBD | Not started | |
| FR-ADM-07 | The admin shall be able to moderate events (remove fake events, close registration, correct deadlines or visib... | Event moderation | PATCH /api/admin/events/{id} | EventModerationService | event_details, posts | Platform admin or in-scope moderator | IT-ADM-07 | TBD | Not started | |
| FR-ADM-08 | The portal shall list user appeals against hiding or suspension; the admin shall accept or reject each appeal ... | AppealsQueue | POST /api/admin/appeals/{id}/accept|reject | AppealReviewService | appeals | Reply mandatory; audited | TC-19, IT-ADM-08 | TBD | Not started | |
| FR-ADM-09 | The admin shall be able to publish announcements for all universities or one university, pinned to feeds. | AnnouncementEditor | CRUD /api/admin/announcements | AnnouncementService | announcements | Global: Platform Admin; university: moderator of that university | IT-ADM-09, SEC-ADM-09 | TBD | Not started | |
| FR-ADM-10 | The Platform Admin shall manage master data: universities, departments, batches and sector tags. | MasterData screens | CRUD /api/admin/universities, /department-catalog, /departments, /batches, /sectors | MasterDataService | universities, department_catalog, departments, batches, sector_tags | Platform Admin only | TC-29, IT-ADM-10, SEC-ADM-10 | TBD | Not started | |
| FR-ADM-11 | The system shall record every admin action (who, what, target, time, IP address) in an audit log that cannot b... | AuditLogViewer (read-only) | GET /api/admin/audit | AuditService append-only; hash chain | audit_logs | No write API; DB grants INSERT/SELECT only | TC-30, IT-ADM-11, SEC-ADM-11 | TBD | Not started | |
| FR-ADM-12 | The Platform Admin shall create, edit, deactivate and assign University Moderator accounts and reset admin cre... | ModeratorManagement | CRUD /api/admin/moderators; POST .../reset-credentials | AdminAccountService | admin_accounts | Platform Admin only | IT-ADM-12, SEC-ADM-12 | TBD | Not started | |
| FR-ADM-13 | The portal shall show analytics (user growth, verification turnaround, events per sector, active universities)... | AnalyticsPage | GET /api/admin/analytics | AnalyticsService (SQL aggregates) | users, verification_requests, posts | Real data only | IT-ADM-13 | TBD | Not started | |
| FR-ADM-14 | The portal shall alert admins when a verification request has waited more than 48 hours. | Overdue alert banner and list | Job OverdueVerificationJob (15 min) | AlertService (idempotent) | admin_alerts | Moderators of that university, else Platform Admins | TC-31, UT-ADM-14, IT-ADM-14 | TBD | Not started | |

## University Moderator

| ID | Requirement | Frontend | Backend | Service / pattern | Entities | Authz and validation | Planned tests | Implementation location | Status | Evidence |
|---|---|---|---|---|---|---|---|---|---|---|
| FR-MOD-01 | A moderator shall log in to the Admin Portal with two-factor authentication (FR-ADM-01). | AdminLogin (same flow) | As FR-ADM-01 | AdminAuthService | admin_accounts | Moderator role | TC-23, IT-MOD-01 | TBD | Not started | |
| FR-MOD-02 | A moderator shall see only the verification queue, users and content of the assigned university. | Scoped admin screens | All /api/admin queries | ScopeResolver in repository queries | all | University scope; 404 outside | TC-26, IT-MOD-02, SEC-MOD-02 | TBD | Not started | |
| FR-MOD-03 | A moderator shall be able to approve or reject students and moderate reported content of the assigned universi... | Approve/reject, moderate | As FR-ADM-03/05/06 | VerificationReviewService, ModerationFacade | as above | Scoped | TC-24, TC-26, IT-MOD-03 | TBD | Not started | |
| FR-MOD-04 | A moderator shall be able to publish announcements for the assigned university only. | University announcements | As FR-ADM-09 | AnnouncementService | announcements | Own university only | IT-MOD-04, SEC-MOD-04 | TBD | Not started | |
| FR-MOD-05 | A moderator shall be able to recommend suspension of a user, which the Platform Admin confirms. | Recommend suspension | POST /api/admin/users/{id}/recommend-suspension | SuspensionRecommendationService | suspension_recommendations | Platform Admin confirms (D-036) | IT-MOD-05 | TBD | Not started | |
| FR-MOD-06 | All moderator actions shall be written to the audit log. | Audit trail | All admin actions | AuditService via AuditEventListener | audit_logs | Same transaction as action | TC-30, IT-MOD-06 | TBD | Not started | |

## Non-functional

| ID | Requirement | Frontend | Backend | Service / pattern | Entities | Authz and validation | Planned tests | Implementation location | Status | Evidence |
|---|---|---|---|---|---|---|---|---|---|---|
| NFR-AVL-01 | The system shall be available 24/7 with at least 99% monthly uptime, excluding announced maintenance. | n/a | Health endpoint, restart policy | Monitoring and uptime report | n/a | n/a | OPS-AVL-01 | TBD | Not started | |
| NFR-CMP-01 | The system shall work on the latest two versions of Chrome, Firefox, Edge and Safari. | n/a | n/a | Playwright chromium, firefox, webkit | n/a | n/a | TC-22, E2E-CMP-01 | TBD | Not started | |
| NFR-FLT-01 | The system shall validate all input and show a clear message; invalid data shall never be saved. | Form errors | All endpoints | Bean Validation, GlobalExceptionHandler | n/a | Nothing invalid persisted | UT-FLT-01, IT-FLT-01 | TBD | Not started | |
| NFR-FLT-02 | If the email gateway fails, the system shall offer the alternative channel for OTP(for instance, SMS). | Channel fallback message | OTP send | SmsProvider/EmailProvider factory fallback | phone_otps | n/a | IT-FLT-02 | TBD | Not started | |
| NFR-FLT-03 | If OCR fails or is inconclusive, the request shall still be queued for admin review with a note, or the studen... | OCR note in queue | OcrJob | Failure path creates note, still queued | verification_attempts | n/a | IT-FLT-03 | TBD | Not started | |
| NFR-LOC-01 | The interface shall use English and support Bangla text input in posts, names and messages (Unicode UTF-8). | Bangla fonts, UTF-8 forms | utf8mb4 everywhere | n/a | all text columns | n/a | IT-LOC-01, E2E-LOC-01 | TBD | Not started | |
| NFR-MNT-01 | Code shall follow MVC, be version-controlled in Git and be documented. | n/a | n/a | Layering, Git, docs, ArchUnit tests | n/a | n/a | UT-ARCH-01 | TBD | Not started | |
| NFR-PER-01 | Pages shall load within 3 seconds for 95% of requests. | n/a | All read endpoints | Indexes, pagination, caching only if measured | n/a | n/a | TC-21, PERF-PER-01 | TBD | Not started | |
| NFR-PER-02 | Search results shall appear within 2 seconds for up to 100,000 records. | n/a | GET /api/search | SearchStrategy; 100,000 seeded rows incl. Bangla | FULLTEXT indexes | n/a | PERF-PER-02 | TBD | Not started | |
| NFR-PER-03 | Chat messages shall be delivered within 1 second under normal load. | n/a | STOMP send/receive | MessagingService, RealtimeBroadcaster | messages | n/a | TC-15, PERF-PER-03 | TBD | Not started | |
| NFR-PER-04 | The system shall support at least 1,000 concurrent users in the first deployment. | n/a | Whole system | Load test 1,000 VUs + 1,000 WS sessions (D-050) | n/a | n/a | TC-21, PERF-PER-04 | TBD | Not started | |
| NFR-PRV-01 | Personal data shall be collected only for the purposes stated in this SRS and shall be handled according to ap... | Privacy policy page | n/a | Data minimisation review | n/a | n/a | REV-PRV-01 | TBD | Not started | |
| NFR-PRV-02 | Students shall be able to permanently delete their account data. | Delete account flow | DELETE /api/me | AccountDeletionService (D-052) | many | Re-authentication required | IT-PRV-02 | TBD | Not started | |
| NFR-PRV-03 | Contact details shall be hidden by default (FR-PROF-08). | Profile DTO | As FR-PROF-08 | ProfileDtoMapper | profiles | n/a | SEC-PRV-03 | TBD | Not started | |
| NFR-REL-01 | The system shall maintain data integrity using database transactions and foreign keys; crashes shall not lose ... | n/a | All write paths | @Transactional boundaries; FKs | all | n/a | IT-REL-01 | TBD | Not started | |
| NFR-REL-02 | Automatic daily database backups shall be kept for at least 14 days. | n/a | Backup script and cron | Ops runbook; restore drill | n/a | n/a | OPS-REL-02 | TBD | Not started | |
| NFR-REL-03 | Scheduled jobs shall retry on failure. | n/a | All jobs | Spring Retry, idempotent job keys | n/a | n/a | IT-REL-03 | TBD | Not started | |
| NFR-REL-04 | The audit log shall be retained for at least 1 year and backed up with the database. | n/a | Audit retention | No purge under 1 year; backup includes audit | audit_logs | n/a | OPS-REL-04, IT-REL-04 | TBD | Not started | |
| NFR-SCL-01 | The architecture shall allow horizontal scaling of the web, WebSocket and storage layers. | n/a | RealtimeBroadcaster, StorageGateway, session store | Stateless app tier; D-051 scale plan | n/a | n/a | REV-SCL-01 | TBD | Not started | |
| NFR-SEC-01 | All traffic shall use HTTPS; passwords shall be hashed. | n/a | Nginx HTTPS, HSTS | Argon2id | n/a | n/a | SEC-SEC-01 | TBD | Not started | |
| NFR-SEC-02 | The system shall protect against SQL injection, XSS and CSRF. | n/a | Whole API | Parameterised JPA, output encoding, CSRF tokens | n/a | n/a | TC-20, SEC-SEC-02 | TBD | Not started | |
| NFR-SEC-03 | Access to every page and API shall be checked by role and verification status on the server; Admin Portal rout... | Route guards are UX only | Two filter chains + AccessPolicy | Role and verification checks server-side | n/a | n/a | TC-27, SEC-SEC-03 | TBD | Not started | |
| NFR-SEC-04 | Uploaded ID card images shall be stored in private storage, accessible only to the owner and authorized admins... | Authorised ID viewer | GET /api/admin/verifications/{id}/id-image (stream) | StorageGateway private; 60 s signed access | attachments | Owner or in-scope reviewer only | SEC-SEC-04, IT-SEC-04 | TBD | Not started | |
| NFR-SEC-05 | OTP and login endpoints shall be rate-limited to prevent brute force and SMS abuse. | n/a | RateLimitFilter + Nginx | Token buckets | n/a | n/a | TC-03, TC-05, SEC-SEC-05 | TBD | Not started | |
| NFR-SEC-06 | Sessions shall use secure, HTTP-only cookies and be invalidated on logout and password change. | n/a | Session config | Spring Session JDBC; invalidate on logout/password change | spring_session | n/a | SEC-SEC-06 | TBD | Not started | |
| NFR-SEC-07 | Admin accounts shall use two-factor authentication, strong password rules, least-privilege roles, optional IP ... | Admin settings | Admin chain | 2FA, 12+ char password, optional IP allow-list, audit | admin_* | n/a | TC-23, SEC-SEC-07 | TBD | Not started | |
| NFR-USA-01 | The interface shall be simple and intuitive so that a new student can sign up and view a feed without reading ... | All student flows | n/a | Usability test with 5+ new students | n/a | n/a | UAT-USA-01 | TBD | Not started | |
| NFR-USA-02 | Navigation menus shall use clear labels and icons; | Nav labels, icons | n/a | Design review against DESIGN.md | n/a | n/a | UAT-USA-02, E2E-A11Y | TBD | Not started | |
| NFR-USA-03 | The interface shall be responsive for screens from 360 px to 1920 px wide. | Responsive layouts 360-1920 px | n/a | CSS tokens and breakpoints | n/a | n/a | E2E-RESP-03 | TBD | Not started | |
| NFR-USA-04 | The system shall follow guidelines for contrast and keyboard navigation where practical. | Contrast and keyboard | n/a | axe in CI, DESIGN.md lint | n/a | WCAG 2.1 AA target (D-056) | E2E-A11Y-04 | TBD | Not started | |
| NFR-USA-05 | Error messages shall be understandable and explain how to fix the problem. | Error messages | Error mapping layer | ApiError codes to messages | n/a | No internal detail exposed | UT-USA-05, E2E-USA-05 | TBD | Not started | |
| NFR-USA-06 | The Admin Portal shall let an admin process a verification request fewer than 1 minute. | Verification review pane and shortcuts | n/a | Timed usability session (D-039a) | n/a | n/a | UAT-USA-06 | TBD | Not started | |

## SRS acceptance test cases (TC-01 to TC-31)

| TC | Module | Scenario | Expected result (SRS) | Automated test(s) | Requirements covered | Status | Evidence |
|---|---|---|---|---|---|---|---|
| TC-01 | Auth | Sign up with valid data and clear ID card image | OTP sent; after OTP the account is Pending Review and appears in the admin queue; after approval it becomes Verified with badge | E2E-TC-01 plus the IT and SEC cases in the module tables | FR-AUTH-01, FR-AUTH-07, FR-AUTH-09 | Not started | |
| TC-02 | Auth | Sign up with an already used email or student ID | Error shown; no duplicate account | E2E-TC-02 plus the IT and SEC cases in the module tables | FR-AUTH-02 | Not started | |
| TC-03 | Auth | Enter wrong OTP 5 times | OTP locked; user must request a new one | E2E-TC-03 plus the IT and SEC cases in the module tables | FR-AUTH-04, NFR-SEC-05 | Not started | |
| TC-04 | Auth | Upload ID card whose number differs from the entered ID | Low OCR score shown to admin; admin rejects with reason; student can resubmit | E2E-TC-04 plus the IT and SEC cases in the module tables | FR-AUTH-06, FR-AUTH-08, FR-ADM-03 | Not started | |
| TC-05 | Auth | Five failed login attempts | Login locked for 15 minutes | E2E-TC-05 plus the IT and SEC cases in the module tables | FR-AUTH-10, NFR-SEC-05 | Not started | |
| TC-06 | Auth | Reset password using OTP | New password works; old one rejected | E2E-TC-06 plus the IT and SEC cases in the module tables | FR-AUTH-11 | Not started | |
| TC-07 | Access | Unverified student tries to post or message | Action blocked with prompt to complete verification | E2E-TC-07 plus the IT and SEC cases in the module tables | FR-AUTH-13 | Not started | |
| TC-08 | Profile | Add a project with GitHub and demo links | Project appears on profile | E2E-TC-08 plus the IT and SEC cases in the module tables | FR-PROF-03 | Not started | |
| TC-09 | Community | CSE student of University A opens own Department | Only University A CSE students and posts are shown | E2E-TC-09 plus the IT and SEC cases in the module tables | FR-COM-02 | Not started | |
| TC-10 | Community | Open CSE Department of existing universities | CSE students and posts from all universities appear | E2E-TC-10 plus the IT and SEC cases in the module tables | FR-COM-04 | Not started | |
| TC-11 | Events | Create a National hackathon post | Visible in National Feed and sidebar sorted by deadline | E2E-TC-11 plus the IT and SEC cases in the module tables | FR-COM-05, FR-EVT-01, FR-EVT-02, FR-EVT-07 | Not started | |
| TC-12 | Events | Deadline passes | Event moves to Past Events automatically and disappears from active feed | E2E-TC-12 plus the IT and SEC cases in the module tables | FR-EVT-05 | Not started | |
| TC-13 | Events | Create an event with a past deadline | Rejected with validation message | E2E-TC-13 plus the IT and SEC cases in the module tables | FR-EVT-09 | Not started | |
| TC-14 | Teams | Press Looking for Teammates; another student requests; creator accepts | Member added and team chat created; | E2E-TC-14 plus the IT and SEC cases in the module tables | FR-TEAM-01, FR-TEAM-02, FR-TEAM-03 | Not started | |
| TC-15 | Messaging | Send private message to an online student | Delivered within 1 second; | E2E-TC-15 plus the IT and SEC cases in the module tables | FR-MSG-02, FR-MSG-05, NFR-PER-03 | Not started | |
| TC-16 | Messaging | Search messages by name | Matching conversations listed | E2E-TC-16 plus the IT and SEC cases in the module tables | FR-MSG-03 | Not started | |
| TC-17 | Search | Search students by department and skill | Correct filtered list returned | E2E-TC-17 plus the IT and SEC cases in the module tables | FR-SRC-01, FR-SRC-02 | Not started | |
| TC-18 | Report | Five different students report one post | Post auto-hidden; owner notified; appears in admin moderation queue | E2E-TC-18 plus the IT and SEC cases in the module tables | FR-REP-02, FR-ADM-05 | Not started | |
| TC-19 | Report | Owner appeals a hidden post; admin restores it | Post visible again; audit log entry created | E2E-TC-19 plus the IT and SEC cases in the module tables | FR-REP-03, FR-ADM-06, FR-ADM-08 | Not started | |
| TC-20 | Security | Attempt SQL injection and script in post body | Input safely handled; no execution | SEC-TC-20 plus the IT and SEC cases in the module tables | NFR-SEC-02 | Not started | |
| TC-21 | Performance | 1,000 concurrent users browse feeds | Page load under 3 seconds | PERF-TC-21 plus the IT and SEC cases in the module tables | NFR-PER-01, NFR-PER-04 | Not started | |
| TC-22 | Compatiblity | Open on Chrome, Firefox, Edge, Safari and mobile | Layout and features work correctly | E2E-TC-22 plus the IT and SEC cases in the module tables | NFR-CMP-01 | Not started | |
| TC-23 | Admin | Admin logs in with correct password but wrong OTP | Login refused; attempt logged | E2E-TC-23 plus the IT and SEC cases in the module tables | FR-ADM-01, FR-MOD-01, NFR-SEC-07 | Not started | |
| TC-24 | Admin | Admin approves a pending student | Status Verified with badge; audit entry written | E2E-TC-24 plus the IT and SEC cases in the module tables | FR-ADM-03, FR-MOD-03 | Not started | |
| TC-25 | Admin | Admin rejects a student without a reason | Blocked; reason is mandatory | E2E-TC-25 plus the IT and SEC cases in the module tables | FR-ADM-03 | Not started | |
| TC-26 | Admin | Moderator of University A opens a University B student | Access denied; only University A data visible | E2E-TC-26 plus the IT and SEC cases in the module tables | FR-MOD-02, FR-MOD-03 | Not started | |
| TC-27 | Admin | Student account opens /admin URLs | Access forbidden (403) | E2E-TC-27 plus the IT and SEC cases in the module tables | NFR-SEC-03 | Not started | |
| TC-28 | Admin | Admin suspends a user | User cannot log in; audit entry written | E2E-TC-28 plus the IT and SEC cases in the module tables | FR-ADM-04 | Not started | |
| TC-29 | Admin | Platform Admin adds a university, department and sector tag | Available in sign-up and filters immediately | E2E-TC-29 plus the IT and SEC cases in the module tables | FR-SEC-01, FR-ADM-10 | Not started | |
| TC-30 | Admin | Try to edit or delete an audit log entry | Not possible from the portal | E2E-TC-30 plus the IT and SEC cases in the module tables | FR-ADM-11, FR-MOD-06 | Not started | |
| TC-31 | Admin | Verification request waits over 48 hours | Admins receive an overdue alert | E2E-TC-31 plus the IT and SEC cases in the module tables | FR-ADM-14 | Not started | |

## Rows with no SRS requirement (record, do not hide)
| Item | Source | Decision | Status |
|---|---|---|---|
| Contribution points | SRS 1.2 scope table only | D-029b: not built | Not implemented |
| Admin 2FA via TOTP | FR-ADM-01 says "OTP" | D-031 | Not started |
| Department catalog | Needed by FR-COM-04 | D-020 | Not started |

## Summary block (regenerate before each phase report)
| Status | Count |
|---|---|
| Completed | 0 |
| Partially completed | 0 |
| Not implemented | 0 |
| Blocked by external dependency | 0 |
| Not tested | 0 |
| Not started | 116 requirements, 31 test cases |
