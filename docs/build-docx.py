from docx import Document
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.oxml import OxmlElement
import datetime

doc = Document()
PROJECT="School Management Software"; SUBTITLE="Universal Primitives: The Design Vocabulary for a Configurable System"
STUDENT="Edidiong Emmanuel Samuel"; INST="NIIT — MMS (Software Engineering)"; SESSION="2025/2026"; DATE="July 2026"; LOC="8 Sirakoro St, Wuse 2, Abuja, Federal Capital Territory, Nigeria"

style=doc.styles['Normal']; style.font.name='Calibri'; style.font.size=Pt(11); style.font.color.rgb=RGBColor(0x1E,0x29,0x3B)
for lv in range(1,4): h=doc.styles[f'Heading {lv}']; h.font.name='Georgia'; h.font.color.rgb=RGBColor(0x0F,0x17,0x2A); h.font.size=Pt([22,15,12][lv-1])

def add_table(doc,hdrs,rows,cols=None):
    t=doc.add_table(rows=1+len(rows),cols=len(hdrs)); t.style='Table Grid'
    for i,h in enumerate(hdrs):
        c=t.rows[0].cells[i]; c.text=h
        for p in c.paragraphs: p.alignment=WD_ALIGN_PARAGRAPH.CENTER
        for r in p.runs: r.bold=True; r.font.size=Pt(9); r.font.color.rgb=RGBColor(0xFF,0xFF,0xFF)
        sh=OxmlElement('w:shd'); sh.set(qn('w:fill'),'0F172A'); sh.set(qn('w:val'),'clear'); c._tc.get_or_add_tcPr().append(sh)
    for ri,row in enumerate(rows):
        for ci,val in enumerate(row):
            c=t.rows[ri+1].cells[ci]; c.text=str(val)
            for p in c.paragraphs:
                for r in p.runs: r.font.size=Pt(9)
            if ri%2==0: sh=OxmlElement('w:shd'); sh.set(qn('w:fill'),'F1F5F9'); sh.set(qn('w:val'),'clear'); c._tc.get_or_add_tcPr().append(sh)
    if cols:
        for i,w in enumerate(cols):
            for row in t.rows: row.cells[i].width=Cm(w)
    return t

def note(doc,text):
    p=doc.add_paragraph(); p.paragraph_format.left_indent=Cm(0.5); r=p.add_run(text); r.font.size=Pt(10); r.font.italic=True; r.font.color.rgb=RGBColor(0x64,0x73,0x8B)

# COVER PAGE
for _ in range(4): doc.add_paragraph()
t=doc.add_paragraph(); t.alignment=WD_ALIGN_PARAGRAPH.CENTER; r=t.add_run(PROJECT); r.font.size=Pt(28); r.font.name='Georgia'; r.font.color.rgb=RGBColor(0x0F,0x17,0x2A); r.bold=True
doc.add_paragraph()
s=doc.add_paragraph(); s.alignment=WD_ALIGN_PARAGRAPH.CENTER; r=s.add_run(SUBTITLE); r.font.size=Pt(14); r.font.name='Georgia'; r.font.color.rgb=RGBColor(0x3B,0x82,0xF6)
for _ in range(3): doc.add_paragraph()
for lb,vl in [("Student",STUDENT),("Institution",INST),("Academic Session",SESSION),("Date of Submission",DATE),("Location",LOC)]:
    p=doc.add_paragraph(); p.alignment=WD_ALIGN_PARAGRAPH.CENTER
    r=p.add_run(f"{lb}: "); r.font.size=Pt(11); r.font.color.rgb=RGBColor(0x64,0x73,0x8B)
    r=p.add_run(vl); r.font.size=Pt(11); r.font.color.rgb=RGBColor(0x1E,0x29,0x3B)
doc.add_page_break()

# TOC
doc.add_heading('Table of Contents',1)
for item in ['1. Introduction and Design Principle','2. The Fourteen Universal Concepts','3. Deep Dive: People and Roles','4. Deep Dive: Structure','5. Deep Dive: Cohorts and Enrollment','6. Deep Dive: Time','7. Deep Dive: Assessment and Records','8. Deep Dive: Credentials and Progression','9. Deep Dive: Resources and Finance','10. The Three Primitives That Determine Success','11. Scope and Boundaries','12. Dependencies Used','13. Implementation Status','14. Conclusion','Appendix A: MoSCoW Decision Matrix','Appendix B: Database Schema Reference']:
    p=doc.add_paragraph(item); p.paragraph_format.space_after=Pt(2)
    for r in p.runs: r.font.size=Pt(11)
doc.add_page_break()

# 1. INTRODUCTION
doc.add_heading('1. Introduction and Design Principle',1)
p=doc.add_paragraph(); r=p.add_run('"A university\'s section, a bootcamp\'s batch, and a primary school\'s class are the same entity wearing different labels."'); r.font.size=Pt(13); r.font.name='Georgia'; r.font.italic=True; r.font.color.rgb=RGBColor(0x3B,0x82,0xF6)
doc.add_paragraph(); doc.add_paragraph('Every learning institution uses the same fourteen core concepts. The differences between them are in the labels they apply and the rules they enforce, not in the underlying data structures. This document presents the design vocabulary that enables a single software system to serve multiple institution types through configuration rather than code changes.')
doc.add_paragraph(); p=doc.add_paragraph(); r=p.add_run('Core principle: Model the abstraction; let configuration supply the label and the rules.'); r.bold=True
doc.add_paragraph('When a developer hardcodes "Class" as a table name, they have built an application for one type of school. When they model "Cohort" as an entity and allow the tenant to label it, they have built a configurable system. This is the distinction between a single-school application and a platform.')
doc.add_page_break()

# 2. FOURTEEN CONCEPTS
doc.add_heading('2. The Fourteen Universal Concepts',1)
doc.add_paragraph('The following table presents the complete vocabulary. Every learning institution on record can be described with these fourteen concepts.')
add_table(doc,['#','Concept','Definition','Common Labels','SchoolHub Table'],[['1','Person / Party','Any human in the system','student, teacher, guardian, admin','platform.app_user'],['2','Role','What a person does; a relationship, not a type','learner, tutor, registrar, bursar','platform.role + role_assignment'],['3','Organizational Unit','Structural container, recursive','institution, campus, department','org_unit (self-ref)'],['4','Offering / Program','The item being taught','course, subject, module, track','offering (self-ref)'],['5','Cohort / Group','How learners are bundled','class, section, batch, stream','cohort + group'],['6','Enrollment','The link: Person to Offering/Cohort','admission, registration','enrollment'],['7','Session / Event','A single time-bound teaching act','lesson, lecture, lab, workshop','session + calendar_event'],['8','Schedule / Calendar','The time skeleton','term, semester, academic year','schedule_period (self-ref)'],['9','Assessment','Any measurement of learning','exam, test, quiz, project','assessment'],['10','Record / Result','The output of an assessment','grade, score, transcript','result'],['11','Credential','Proof of completion','certificate, diploma, degree','credential'],['12','Progression Rule','Logic for advancing','prerequisite, promotion, pass mark','progression_rule + result'],['13','Resource','Physical or virtual item consumed','room, lab, equipment','resource + resource_permit'],['14','Financial','Money flows','tuition, fee, invoice, scholarship','fee_invoice + payment + waiver']],cols=[0.5,2.2,3.2,3.5,3.8])
doc.add_page_break()

# 3-14: Deep dives, critical primitives, scope, dependencies, implementation, conclusion, appendices — condensed for build script
sections = [
    ("3. People and Roles", [
        ("Primitive 1: Person / Party","Any human in the system. Represented by a single table rather than separate tables for each persona type.","student, pupil, learner; teacher, lecturer; guardian, parent; admin, registrar","platform.app_user","The amateur approach creates separate tables for Student, Teacher, and Guardian. The professional approach uses one Person table with roles assigned through a join table. A single individual can hold multiple roles across multiple institutions without duplication.",["A single platform.app_user row links to teacher, student, and guardian profiles across schools.","The avatar is stored on app_user and follows the person across all tenant memberships.","Role assignments are per-tenant: a person can be an Admin at one school and a Teacher at another."]),
        ("Primitive 2: Role","What a person does within a given context. A relationship between a person and a tenant, not an attribute of the person.","learner, tutor, registrar, bursar, librarian, moderator, platform owner","platform.role + platform.role_assignment","The role_assignment table is the authoritative source for what roles a user holds. A user may hold multiple role assignments across multiple tenants. The is_default flag determines which assignment mints the JWT at login.",["PLATFORM_OWNER (id=1): Operates SchoolHub itself.","MODERATOR (id=2): Platform support staff.","ADMIN (id=3): Manages one school.","PRINCIPAL (id=4): Senior staff with admin-equivalent access.","TEACHER (id=5): Records attendance and enters grades.","STUDENT (id=6): Views personal results and fees.","PARENT (id=7): Monitors progress for linked children.","BURSAR (id=8): Manages invoices and payments.","LIBRARIAN (id=9): Manages the library catalogue."])
    ]),
    ("4. Structure: Organizational Units and Offerings", [
        ("Primitive 3: Organizational Unit","A recursive structural container with unlimited nesting depth.","institution, campus, school, faculty, department","org_unit (self-ref via parent_id)","Self-referential design supports arbitrary depth without schema changes. Type labels are freeform text fields rather than a database enum.",["Deletion is governed by workflow: marking a unit pending_deletion opens a seven-day protest window.","Status: active or pending_deletion."]),
        ("Primitive 4: Offering / Program","The item being taught. Also recursive: Program contains Course contains Module.","course, subject, module, unit, program, track","offering (self-ref) + offering_org_unit + offering_prerequisite","Mirrors the recursive pattern of organizational units. Cross-listing through offering_org_unit allows one offering to belong to multiple departments.",["credit_weight distinguishes a 3-credit course from a 1-credit lab.","Together, Org Unit and Offering answer: who teaches what, and where?"])
    ]),
    ("5. Cohorts and Enrollment", [
        ("Primitive 5: Cohort / Group","How learners are bundled. Recreated every schedule period.","class, section, batch, stream, form","cohort + group","A cohort is flat: it never nests inside another. Continuity of 'SS2A becomes SS3A' is tracked through enrollment history.",["capacity: nullable integer for head-set limits.","is_prime_level: marks graduating level for future alumnus status."]),
        ("Primitive 6: Enrollment","The central hub of the system. An independent entity with its own lifecycle.","admission, registration, matriculation","enrollment","The most consequential architectural decision. Enrollment binds a Student to a Cohort, Offering, or Group for one schedule period. Nearly every operational feature depends on it.",["Status lifecycle: active, transferred_out, withdrawn, graduated, repeating, pending, pending_payment.","Must have at least one target (cohort, offering, or group).","ended_on and closed_reason provide an audit trail."])
    ]),
    ("6. Time: Sessions and Schedules", [
        ("Primitive 7: Session / Event","A single time-bound teaching act with a defined type, location, and audience.","lesson, lecture, period, seminar, lab","session + session_attendance","Sessions represent the operational unit of teaching. They book resources, target cohort_offerings, and carry per-session attendance.",["recurrence_rule for repeating sessions.","Per-session attendance coexists with legacy daily attendance during migration."]),
        ("Primitive 8: Schedule / Calendar","The time skeleton. Self-referential: Term nests under Academic Year.","term, semester, trimester, academic year","schedule_period (self-ref)","The primitive that most frequently causes system failure. The self-referential design supports any nesting depth, but business rules multiply combinatorially across institution types.",["Scoped to K-12 term-based schools and vocational institutes.","Phase 1: schema and basic CRUD. Full recursion in Phase 2."])
    ]),
    ("7. Assessment and Records", [
        ("Primitive 9: Assessment","Any measurement of learning with a publish gate separating data entry from visibility.","exam, test, quiz, assignment, project","assessment","Assessments include a publish flag modelling real-world review-before-release workflows. Weight enables composite scoring.",["weight for composite scoring (CA 30%, Exam 70%).","published: controls student visibility.","pass_mark_percent configurable per assessment."]),
        ("Primitive 10: Record / Result","The output of an assessment. One row per student per assessment.","grade, score, mark, transcript","result","Transcripts are generated on demand from live result rows. Corrected scores propagate instantly. Accuracy is prioritised over immutability for the current scope.",["is_resit and penalty for retaken or late assessments.","UNIQUE (assessment_id, student_id)."])
    ]),
    ("8. Credentials and Progression", [
        ("Primitive 11: Credential","Proof of completion issued by the institution to a specific individual.","certificate, diploma, degree, badge","credential","Credentials carry a criteria reference linking back to the rule or process that generated them. Revocation escalates to moderator through the workflow system.",["auto_issue_credential on progression_rule for automatic minting.","artifact_url reserved for future PDF certificates."]),
        ("Primitive 12: Progression Rule","Logic determining whether a learner may advance to the next stage.","prerequisite, promotion, pass mark","progression_rule + progression_result","Rules are evaluated to produce stored results. ATTENDANCE_MINIMUM, PREREQUISITE_COMPLETION, and MANUAL_SCORE types determine what data is inspected.",["scope_type: COHORT or OFFERING.","Fixed rule-type menu rather than custom formula parser.","Status: proposed, active, retired."])
    ]),
    ("9. Resources and Finance", [
        ("Primitive 13: Resource","Any physical or virtual item consumed during instruction.","room, laboratory, equipment, virtual room","resource (self-ref) + resource_permit","Resources are flat and institution-wide. Equipment nests under rooms. Resources may be staff_only; permits grant individual access.",["Kind: room, virtual, equipment.","capacity: nullable for unlimited.","staff_only + resource_permit for access control."]),
        ("Primitive 14: Financial","All money flows: invoices, payments, waivers, scholarships.","tuition, fee, invoice, scholarship","fee_invoice + fee_payment + waiver + scholarship","Invoices fan out to students, classes, or the whole school. Draft-then-approve separates staff submission from admin authorisation. Payment uses an adapter pattern: Stripe integration is in progress.",["Currency configurable per tenant (default NGN).","Scholarship rules: percentage-based discounts.","Waivers reduce balances without money changing hands.","Refunds: negative payment rows netting out automatically."])
    ]),
]

for heading,prims in sections:
    doc.add_heading(heading,1)
    for ptitle,pwhat,plabels,ptable,pinsight,pbullets in prims:
        doc.add_heading(ptitle,2)
        pp=doc.add_paragraph(); r=pp.add_run(pwhat); r.font.size=Pt(11); r.font.italic=True; r.font.color.rgb=RGBColor(0x3B,0x82,0xF6)
        doc.add_paragraph(); doc.add_paragraph(f'Common labels: {plabels}'); doc.add_paragraph(f'SchoolHub implementation: {ptable}')
        doc.add_paragraph(); pp=doc.add_paragraph(); r=pp.add_run('Design rationale: '); r.bold=True; pp.add_run(pinsight)
        for b in pbullets: doc.add_paragraph(b,style='List Bullet')
        doc.add_paragraph()
    doc.add_page_break()

# 10. THREE CRITICAL
doc.add_heading('10. The Three Primitives That Determine Success',1)
doc.add_paragraph('Three of the fourteen primitives are existential. If modelled incorrectly, the system collapses at the first real-world edge case.')
for title,mistake,correct,explanation in [
    ("Primitive 2: Role","Creating separate database tables for Student, Teacher, and Guardian.","A single Person table with Role as a relationship through a join table.","A PhD student who also teaches must not be a schema contradiction. With the correct model, they simply hold two role assignments."),
    ("Primitive 6: Enrollment","Adding a class_id column to the Student record.","Enrollment as an independent entity with its own lifecycle, dates, status, and relationships.","Attendance, grading, billing, and progression all depend on Enrollment rather than on Student directly. The legacy student.class_id is transitional."),
    ("Primitive 8: Schedule","A single hardcoded Term or Semester model with fixed nesting levels.","A self-referential schedule_period table supporting any depth of nesting with configurable labels.","This primitive most frequently causes system failure. Different institution types require fundamentally different time models. Scoped to K-12 term-based schools.")
]:
    doc.add_heading(title,2)
    p=doc.add_paragraph(); r=p.add_run('Incorrect: '); r.bold=True; r.font.color.rgb=RGBColor(0xDC,0x26,0x26); p.add_run(mistake)
    p=doc.add_paragraph(); r=p.add_run('Correct: '); r.bold=True; r.font.color.rgb=RGBColor(0x10,0xB9,0x81); p.add_run(correct)
    doc.add_paragraph(explanation)
doc.add_page_break()

# 11. SCOPE
doc.add_heading('11. Scope and Boundaries',1)
p=doc.add_paragraph(); r=p.add_run('"Works for all learning institutions" is a seductive but dangerous goal.'); r.font.size=Pt(13); r.font.name='Georgia'; r.font.italic=True; r.font.color.rgb=RGBColor(0xF5,0x9E,0x0B)
doc.add_paragraph(); doc.add_paragraph('The fourteen primitives constitute the vocabulary. The rules connecting them constitute the cost. A truly universal system is a career-length undertaking.')
doc.add_paragraph('Each institution type requires different business logic on the same fourteen tables. Supporting all simultaneously means every code path branches on institution type.')
doc.add_paragraph(); p=doc.add_paragraph(); r=p.add_run('Scope decision: K-12 private schools and vocational institutes.'); r.bold=True; r.font.color.rgb=RGBColor(0x10,0xB9,0x81)
doc.add_paragraph('Achievable within the project timeframe. Defensible under academic examination. Extensible: the schema already supports deeper nesting.')
note(doc,'Two specific institution types is honest, testable, and defensible.')
doc.add_page_break()

# 12. DEPENDENCIES USED (NEW)
doc.add_heading('12. Dependencies Used',1)
doc.add_paragraph('The following table documents the technology stack and external dependencies employed in the SchoolHub project.')
add_table(doc,['Category','Dependency','Version','Purpose'],[
    ['Framework','Spring Boot','3.5.14','Application framework; inversion of control and dependency injection'],
    ['Framework','Spring Security','3.5.14','Authentication, authorisation, and role-based access control'],
    ['Framework','Spring Data JPA','3.5.14','Object-relational mapping, repository abstraction, and query derivation'],
    ['Framework','Spring Web (Apache Tomcat)','10.1.54','Embedded HTTP server and RESTful endpoint hosting'],
    ['Framework','Spring Boot Actuator','3.5.14','Application health checks and operational monitoring endpoints'],
    ['Framework','Spring Boot Validation','3.5.14','Declarative request DTO validation'],
    ['Framework','Spring Boot Mail','3.5.14','Email notification support (AuthService only)'],
    ['Authentication','JJWT (io.jsonwebtoken)','0.12.6','JSON Web Token creation, signing, and verification'],
    ['Database','PostgreSQL JDBC Driver','42.7.x','Postgres database connectivity with schema-per-tenant isolation'],
    ['Connection Pool','HikariCP','(bundled)','High-performance JDBC connection pooling'],
    ['ORM','Hibernate ORM','6.6.49','Entity lifecycle management and schema auto-detection'],
    ['Serialisation','Jackson','(bundled)','JSON serialisation and deserialisation'],
    ['Icons','Lucide Icons','(CDN)','Lightweight icon library for the user interface'],
    ['Authentication','Google Identity Services','(CDN)','OAuth 2.0 popup-based Google sign-in'],
    ['Build','Apache Maven (wrapper)','3.9.15','Dependency management, compilation, and executable JAR packaging'],
    ['Runtime','Java (OpenJDK Temurin)','25.0.2','Java runtime environment'],
    ['Database Server','PostgreSQL','18.3','Relational database management system'],
],cols=[2.2,3.5,1.8,6.0])
doc.add_paragraph()
doc.add_paragraph('All Java dependencies are managed through the Spring Boot parent POM (version 3.5.14) and Maven wrapper, ensuring consistent version resolution across all four microservices.')
doc.add_page_break()

# 13. IMPLEMENTATION STATUS
doc.add_heading('13. Implementation Status',1)
doc.add_paragraph('Each primitive mapped to its implementation and current development status.')
add_table(doc,['#','Primitive','Database Table','Service Class','Status'],[
    ['1','Person / Party','platform.app_user','AuthService','Complete'],['2','Role','platform.role + role_assignment','AuthService','Complete'],['3','Org Unit','{schema}.org_unit','OrgUnitService','Complete'],['4','Offering','{schema}.offering','OfferingService','Complete'],['5','Cohort','{schema}.cohort + group','CohortService','Complete'],['6','Enrollment','{schema}.enrollment','EnrollmentService','Complete'],['7','Session','{schema}.session','SessionService','Complete'],['8','Schedule','{schema}.schedule_period','SchedulePeriodService','Phase 1'],['9','Assessment','{schema}.assessment','AcademicService','Complete'],['10','Result','{schema}.result','AcademicService','Complete'],['11','Credential','{schema}.credential','CredentialService','Complete'],['12','Progression','{schema}.progression_rule','ProgressionRuleService','Complete'],['13','Resource','{schema}.resource','ResourceService','Complete'],['14','Financial','{schema}.fee_invoice + payment','FeeService','Complete']
],cols=[0.5,2.0,3.2,2.8,1.8])
doc.add_paragraph()
doc.add_paragraph('All fourteen primitives are implemented. The system uses a microservices architecture with four services (Gateway:9000, Auth:9001, Tenant:9002, School:9003) connected to PostgreSQL with schema-per-tenant isolation.')
doc.add_page_break()

# 14. CONCLUSION
doc.add_heading('14. Conclusion',1)
doc.add_paragraph('Every learning institution can be described with fourteen universal concepts. The differences are in the labels and the rules, not in the underlying data structures. By modelling the abstraction and treating the label as configuration data, a single system can serve multiple institution types.')
doc.add_paragraph('Three primitives determine whether a system is genuinely configurable: Role must be a relationship, Enrollment must be an independent entity, and Schedule must support self-referential nesting. Getting these right distinguishes a platform from a single-school application.')
doc.add_paragraph('Scope discipline is essential. Scoping deliberately to K-12 private schools and vocational institutes is achievable, defensible, and honest.')
doc.add_paragraph('The SchoolHub implementation demonstrates all fourteen primitives in working code, with twelve of fifteen planned features complete and the remainder actively in development.')
doc.add_page_break()

# APPENDIX A
doc.add_heading('Appendix A: MoSCoW Decision Matrix',1)
add_table(doc,['Priority','Feature','Status'],[
    ['Must','Authentication and authorisation','Complete'],['Must','Multi-tenant schema isolation','Complete'],['Must','Student, teacher, and class management','Complete'],['Must','Attendance recording and result entry','Complete'],['Must','Fee invoicing and payment processing','Complete'],['Must','Calendar and event management','Complete'],['Should','Cohort, offering, and enrollment model','Complete'],['Should','Library management system','Complete'],['Should','Notifications system','Complete'],['Should','Workflow system (propose, confirm, protest)','Complete'],['Should','Credentials and progression rules','Complete'],['Should','Resource and room booking','Complete'],['Could','Stripe payment integration','In progress'],['Could','Template-specific school seeding','Deferred'],['Could','Subscription plan student cap enforcement','Deferred'],['Could','Self-service password recovery','Deferred'],['Could','School hard-delete with schema removal','Deferred'],['Will not','University semester model','Out of scope'],['Will not','Rolling bootcamp intake model','Out of scope'],['Will not','Corporate LMS competency tracking','Out of scope']
],cols=[2.0,8.0,3.0])
doc.add_paragraph(); doc.add_paragraph('All Must and Should items are implemented and verified. Could items are deferred to future cycles. Will Not items define the scope boundary.')
doc.add_page_break()

# APPENDIX B
doc.add_heading('Appendix B: Database Schema Reference',1)
doc.add_paragraph('Schema-per-tenant isolation: the platform schema contains shared registry and authentication tables. Each school receives its own PostgreSQL schema created at signup.')
doc.add_heading('Platform Schema',2)
for t in ['platform.role — Nine system roles','platform.subscription_plan — Pricing tiers','platform.tenant — School registry','platform.app_user — Single user table','platform.role_assignment — User-to-role mappings','platform.token_blacklist — Logout enforcement','platform.audit_log — Cross-service audit trail','platform.notification — User inbox','platform.workflow_request / workflow_protest — Governance','platform.invite / invite_redemption — Invitations']:
    doc.add_paragraph(t,style='List Bullet')
doc.add_heading('Tenant Schema (per-school)',2)
for t in ['teacher, subject, school_class, class_subject, student, guardian, student_guardian','assessment, result','attendance (daily), session_attendance (per-session)','calendar_event','fee_invoice, fee_payment, fee_waiver, scholarship_rule, financial_settings, fee_category','cohort, group, cohort_offering, enrollment','org_unit, offering, offering_org_unit, offering_prerequisite','schedule_period, session, resource, resource_permit','progression_rule, progression_result, credential','library_staff, library_student, book, borrow_request, borrow_record, book_flag, library_fine_rule']:
    doc.add_paragraph(t,style='List Bullet')

out=r"C:\Users\Deus\Desktop\Springworld\SchoolHub refix\docs\School Management Software.docx"
doc.save(out); print(f"DOCX done: {out}")
