const pptxgen = require("pptxgenjs");
const pres = new pptxgen();
pres.layout = "LAYOUT_16x9";
pres.author = "Edidiong Emmanuel Samuel";
pres.title = "School Management Software — Universal Primitives";

const C = { bg:"0F172A", card:"1E293B", accent:"3B82F6", accent2:"6366F1", warm:"F59E0B", green:"10B981", text:"F1F5F9", mute:"94A3B8", line:"334155", white:"FFFFFF" };
const FONT_H = "Georgia", FONT_B = "Calibri";
const PROJECT="School Management Software", STUDENT="Edidiong Emmanuel Samuel", INST="NIIT — MMS (Software Engineering)", SESSION="2025/2026", DATE="July 2026", LOC="8 Sirakoro St, Wuse 2, Abuja, FCT, Nigeria";

function addFooter(s, n) {
  s.addShape(pres.shapes.RECTANGLE, { x:0, y:5.15, w:10, h:0.475, fill:{color:C.card} });
  s.addText(`${STUDENT}  |  ${INST}  |  ${SESSION}`, { x:0.4, y:5.18, w:7, h:0.38, fontSize:8, fontFace:FONT_B, color:C.mute, margin:0 });
  s.addText(String(n), { x:9.0, y:5.18, w:0.6, h:0.38, fontSize:8, fontFace:FONT_B, color:C.mute, align:"right", margin:0 });
}
function slideTitle(s, t) { s.addText(t, { x:0.6, y:0.25, w:8.8, h:0.5, fontSize:26, fontFace:FONT_H, color:C.text, bold:true, margin:0 }); }
function card(s,x,y,w,h,f) { s.addShape(pres.shapes.RECTANGLE, {x,y,w,h, fill:{color:f||C.card}, shadow:{type:"outer",blur:3,offset:1,angle:135,color:"000000",opacity:0.25}}); }

// SLIDE 1 — TITLE
(()=>{ const s=pres.addSlide(); s.background={color:C.bg};
  s.addText(PROJECT,{x:0.7,y:0.6,w:8.6,h:0.8,fontSize:34,fontFace:FONT_H,color:C.text,bold:true,align:"center"});
  s.addText("Universal Primitives: The Design Vocabulary\nfor a Configurable System",{x:0.7,y:1.4,w:8.6,h:0.7,fontSize:16,fontFace:FONT_H,color:C.accent,align:"center"});
  s.addShape(pres.shapes.RECTANGLE,{x:3.0,y:2.3,w:4,h:0.012,fill:{color:C.accent}});
  card(s,1.5,2.6,7.0,2.2);
  [{label:"Student",value:STUDENT},{label:"Institution",value:INST},{label:"Academic Session",value:SESSION},{label:"Date",value:DATE},{label:"Location",value:LOC}].forEach((d,i)=>{
    s.addText(d.label,{x:1.8,y:2.75+i*0.42,w:2.5,h:0.35,fontSize:12,fontFace:FONT_B,color:C.mute,margin:0});
    s.addText(d.value,{x:4.3,y:2.75+i*0.42,w:4.0,h:0.35,fontSize:12,fontFace:FONT_B,color:C.text,margin:0});
  });
  addFooter(s,1);
})();

// SLIDE 2 — INTRODUCTION
(()=>{ const s=pres.addSlide(); s.background={color:C.bg}; slideTitle(s,"Introduction");
  card(s,0.6,1.0,8.8,1.6);
  s.addText([{text:"\u201C",options:{fontSize:32,fontFace:FONT_H,color:C.accent}},{text:"A university\u2019s section, a bootcamp\u2019s batch, and a primary school\u2019s class are the same entity wearing different labels.",options:{fontSize:17}},{text:"\u201D",options:{fontSize:32,fontFace:FONT_H,color:C.accent}}],{x:0.9,y:1.1,w:8.2,h:1.4,fontFace:FONT_H,color:C.text,align:"center",valign:"middle"});
  [{title:"Model the Abstraction",body:"One entity, one table, one set of business logic. Never duplicate code for each institution type."},{title:"Configure the Label",body:"The name, rules, and behaviour come from the tenant\u2019s template. Changing \u201CClass\u201D to \u201CSection\u201D is a configuration change, not a code change."}].forEach((c,i)=>{
    const cx=0.6+i*4.55; card(s,cx,3.0,4.15,1.7);
    s.addText(c.title,{x:cx+0.25,y:3.1,w:3.65,h:0.35,fontSize:15,fontFace:FONT_H,color:C.accent,bold:true,margin:0});
    s.addText(c.body,{x:cx+0.25,y:3.55,w:3.65,h:0.95,fontSize:11,fontFace:FONT_B,color:C.mute,margin:0});
  });
  addFooter(s,2);
})();

// SLIDE 3 — THE 14 CONCEPTS TABLE
(()=>{ const s=pres.addSlide(); s.background={color:C.bg}; slideTitle(s,"The Fourteen Universal Concepts");
  const hdr=t=>({text:t,options:{fill:{color:C.accent},color:C.white,bold:true,fontFace:FONT_H,fontSize:10,align:"center",valign:"middle"}});
  const cell=(t,a)=>({text:t,options:{fill:{color:a?C.card:C.bg},color:C.text,fontFace:FONT_B,fontSize:9,valign:"middle",border:{pt:0.5,color:C.line}}});
  const nc=(t,a)=>({text:t,options:{fill:{color:a?C.card:C.bg},color:C.accent,fontFace:FONT_B,fontSize:9,bold:true,valign:"middle",border:{pt:0.5,color:C.line}}});
  const rows=[["1","Person / Party","Any human in the system","student, teacher, guardian, admin"],["2","Role","What a person does (relationship, not a type)","learner, tutor, registrar, bursar"],["3","Organizational Unit","Structural container, recursive","institution, campus, department"],["4","Offering / Program","The item being taught","course, subject, module, track"],["5","Cohort / Group","How learners are bundled","class, section, batch, stream"],["6","Enrollment","The link binding Person to Offering/Cohort","admission, registration"],["7","Session / Event","A single time-bound teaching act","lesson, lecture, lab, workshop"],["8","Schedule / Calendar","The time skeleton","term, semester, academic year"],["9","Assessment","Any measurement of learning","exam, test, quiz, project"],["10","Record / Result","The output of an assessment","grade, score, transcript"],["11","Credential","Proof of completion","certificate, diploma, degree"],["12","Progression Rule","Logic for advancing","prerequisite, promotion, pass mark"],["13","Resource","Physical or virtual item consumed","room, lab, equipment"],["14","Financial","Money flows","tuition, fee, invoice, scholarship"]];
  let td=[[hdr("#"),hdr("Concept"),hdr("Definition"),hdr("Common Labels")]];
  rows.forEach((r,i)=>td.push([nc(r[0],i%2===0),cell(r[1],i%2===0),cell(r[2],i%2===0),cell(r[3],i%2===0)]));
  s.addTable(td,{x:0.3,y:0.95,w:9.4,colW:[0.4,1.7,2.8,4.5],rowH:[0.30,...rows.map(()=>0.27)],border:{pt:0.5,color:C.line}});
  addFooter(s,3);
})();

// SLIDES 4-10: PAIR SLIDES
function pairSlide(s,n,t,p1,p2,it,ib){
  slideTitle(s,t);
  card(s,0.6,1.0,4.15,1.8); s.addShape(pres.shapes.OVAL,{x:0.8,y:1.15,w:0.45,h:0.45,fill:{color:C.accent}}); s.addText(p1.num,{x:0.8,y:1.15,w:0.45,h:0.45,fontSize:16,fontFace:FONT_H,color:C.white,bold:true,align:"center",valign:"middle"}); s.addText(p1.title,{x:1.4,y:1.15,w:3.1,h:0.4,fontSize:16,fontFace:FONT_H,color:C.text,bold:true,margin:0}); s.addText(p1.body,{x:0.85,y:1.65,w:3.65,h:0.9,fontSize:10,fontFace:FONT_B,color:C.mute,margin:0});
  card(s,5.25,1.0,4.15,1.8); s.addShape(pres.shapes.OVAL,{x:5.45,y:1.15,w:0.45,h:0.45,fill:{color:C.warm}}); s.addText(p2.num,{x:5.45,y:1.15,w:0.45,h:0.45,fontSize:16,fontFace:FONT_H,color:C.bg,bold:true,align:"center",valign:"middle"}); s.addText(p2.title,{x:6.05,y:1.15,w:3.1,h:0.4,fontSize:16,fontFace:FONT_H,color:C.text,bold:true,margin:0}); s.addText(p2.body,{x:5.5,y:1.65,w:3.65,h:0.9,fontSize:10,fontFace:FONT_B,color:C.mute,margin:0});
  card(s,0.6,3.1,8.8,1.6); s.addText(it,{x:0.85,y:3.2,w:8.3,h:0.3,fontSize:11,fontFace:FONT_B,color:C.accent2,bold:true,charSpacing:2,margin:0}); s.addText(ib,{x:0.85,y:3.55,w:8.3,h:0.95,fontSize:11,fontFace:FONT_B,color:C.mute,margin:0});
  addFooter(s,n);
}
// 4
(()=>{ const s=pres.addSlide(); s.background={color:C.bg};
  pairSlide(s,4,"People and Roles",
    {num:"1",title:"Person / Party",body:"Any human in the system. Represented by a single table, not separate tables for each persona. One person can hold multiple roles across institutions."},
    {num:"2",title:"Role",body:"A relationship, not a type. One person can simultaneously be a teacher, a parent, and a student. The professional approach uses a join table."},
    "IMPLEMENTATION","SchoolHub uses platform.app_user as the single user table. Roles are assigned per-tenant through platform.role_assignment. A PhD student who teaches undergraduates holds two role assignments, not two user accounts.");
})();
// 5
(()=>{ const s=pres.addSlide(); s.background={color:C.bg};
  pairSlide(s,5,"Structure: Org Units and Offerings",
    {num:"3",title:"Organizational Unit",body:"A recursive structural container. Institution to Campus to Faculty to Department. Unlimited depth with freeform type labels stored as data."},
    {num:"4",title:"Offering / Program",body:"The item being taught. Also recursive: Program to Course to Module. Cross-listed to multiple org units via a link table."},
    "IMPLEMENTATION","org_unit uses self-referential parent_id for unlimited nesting. offering mirrors this pattern. offering_org_unit enables cross-listing. Together they answer: who teaches what, and where?");
})();
// 6
(()=>{ const s=pres.addSlide(); s.background={color:C.bg};
  pairSlide(s,6,"Cohorts and Enrollment",
    {num:"5",title:"Cohort / Group",body:"How learners are bundled. Recreated every schedule period. Continuity across periods is tracked through enrollment history."},
    {num:"6",title:"Enrollment",body:"The central hub. Not a field on a student but its own entity. Carries dates, status, results, and fees. Attendance, grading, and billing all depend on it."},
    "IMPLEMENTATION","enrollment binds a student to a cohort, offering, or group for one schedule_period. Status lifecycle: active, transferred_out, withdrawn, graduated, repeating. The legacy student.class_id is transitional.");
})();
// 7
(()=>{ const s=pres.addSlide(); s.background={color:C.bg};
  pairSlide(s,7,"Time: Sessions and Schedules",
    {num:"7",title:"Session / Event",body:"A single time-bound teaching act. Types include lesson, exam, seminar, and lab. Carries start/end times, resource booking, and recurrence rules."},
    {num:"8",title:"Schedule / Calendar",body:"The time skeleton. Self-referential: a Term nests under a Year. This is the axis that most frequently causes system failure when designed too rigidly."},
    "IMPLEMENTATION","schedule_period uses self-referential nesting for terms within academic years. session books resources and supports recurrence rules. Scoped to term-based K-12 schools and vocational institutes.");
})();
// 8
(()=>{ const s=pres.addSlide(); s.background={color:C.bg};
  pairSlide(s,8,"Assessment and Records",
    {num:"9",title:"Assessment",body:"Any measurement of learning. Carries a publish gate so teachers can enter scores privately before releasing them. Supports weighting for composite scoring."},
    {num:"10",title:"Record / Result",body:"The output of an assessment. One row per student per assessment. Transcripts are generated on demand from live result rows rather than stored as frozen snapshots."},
    "IMPLEMENTATION","assessment uses a publish flag to control visibility. result rows are unique per assessment-student pair with resit flags and penalties. Transcripts are live queries so corrected scores propagate instantly.");
})();
// 9
(()=>{ const s=pres.addSlide(); s.background={color:C.bg};
  pairSlide(s,9,"Credentials and Progression",
    {num:"11",title:"Credential",body:"Proof of completion issued by the institution. Can be auto-generated when a progression rule is satisfied or issued manually. Revocation requires moderator approval."},
    {num:"12",title:"Progression Rule",body:"Logic for advancing. Rule types: attendance minimums, prerequisite completion, manual score thresholds. Results are evaluated once and stored."},
    "IMPLEMENTATION","progression_rule defines the criteria; progression_result stores the outcome. A rule can auto_issue_credential to mint a certificate upon passing. Together they close the enrollment-to-graduation loop.");
})();
// 10
(()=>{ const s=pres.addSlide(); s.background={color:C.bg};
  pairSlide(s,10,"Resources and Finance",
    {num:"13",title:"Resource",body:"Any physical or virtual item consumed. Rooms, laboratories, equipment. Self-referential so equipment nests under a room. Supports capacity limits and staff-only restrictions."},
    {num:"14",title:"Financial",body:"All money flows. Invoices, payments, waivers, scholarships. Supports draft-then-approve workflows for staff-submitted charges and online payment gateway integration."},
    "IMPLEMENTATION","resource and resource_permit manage rooms and equipment bookings. fee_invoice supports fan-out to classes or students with categories for fees, books, and other charges. Stripe integration is currently in progress.");
})();

// SLIDE 11 — THREE CRITICAL
(()=>{ const s=pres.addSlide(); s.background={color:C.bg}; slideTitle(s,"The Three Primitives That Determine Success");
  [{num:"2",title:"Role", mistake:"Separate tables for Student and Teacher", correct:"Person table with Role as a join relationship", note:"A PhD student who teaches undergraduates must not be a schema contradiction."},
   {num:"6",title:"Enrollment", mistake:"A class_id column on the Student row", correct:"Enrollment as an independent entity with full lifecycle", note:"Attendance, grading, billing, and progression all depend on Enrollment, not Student."},
   {num:"8",title:"Schedule", mistake:"A single hardcoded Term or Semester model", correct:"Self-referential schedule_period with configurable nesting", note:"Different institution types require fundamentally different time models. Scoped to K-12 term-based institutions."}
  ].forEach((it,i)=>{
    const cy=1.05+i*1.3; card(s,0.6,cy,8.8,1.1);
    s.addShape(pres.shapes.OVAL,{x:0.8,y:cy+0.15,w:0.5,h:0.5,fill:{color:i===0?C.warm:i===1?C.accent:C.accent2}});
    s.addText(it.num,{x:0.8,y:cy+0.15,w:0.5,h:0.5,fontSize:16,fontFace:FONT_H,color:i===0?C.bg:C.white,bold:true,align:"center",valign:"middle"});
    s.addText(it.title,{x:1.5,y:cy+0.08,w:1.5,h:0.35,fontSize:16,fontFace:FONT_H,color:C.text,bold:true,margin:0});
    s.addText([{text:"Incorrect: ",options:{bold:true,color:"EF4444"}},{text:it.mistake,options:{color:C.mute}},{text:"    Correct: ",options:{bold:true,color:C.green}},{text:it.correct,options:{color:C.text}}],{x:1.5,y:cy+0.45,w:7.6,h:0.3,fontSize:10,fontFace:FONT_B,margin:0});
    s.addText(it.note,{x:1.5,y:cy+0.75,w:7.6,h:0.28,fontSize:9,fontFace:FONT_B,color:C.mute,italic:true,margin:0});
  });
  addFooter(s,11);
})();

// SLIDE 12 — SCOPE
(()=>{ const s=pres.addSlide(); s.background={color:C.bg}; slideTitle(s,"Scope and Boundaries");
  s.addText("14",{x:0.6,y:1.1,w:2.5,h:1.2,fontSize:72,fontFace:FONT_H,color:C.accent,bold:true});
  s.addText([{text:"primitives form the vocabulary.",options:{fontSize:18}}],{x:3.2,y:1.25,w:6.2,h:0.5,fontFace:FONT_B,color:C.text,margin:0});
  s.addText([{text:"The rules connecting them determine the cost.",options:{fontSize:18,color:C.warm}}],{x:3.2,y:1.7,w:6.2,h:0.5,fontFace:FONT_B,margin:0});
  card(s,0.6,2.6,8.8,1.4);
  s.addText([{text:"A universal system supporting every institution type is a career, not a capstone project.",options:{bold:true,color:C.warm}},{text:" The rules multiply combinatorially across institution types. Each demands fundamentally different business logic around the same fourteen tables.",options:{color:C.text}}],{x:0.85,y:2.7,w:8.3,h:1.1,fontSize:12,fontFace:FONT_B,margin:0});
  card(s,0.6,4.2,8.8,0.8);
  s.addText([{text:"Scope decision: ",options:{bold:true,color:C.green}},{text:"K-12 private schools and vocational institutes. This is specific, buildable, and defensible under academic examination.",options:{color:C.mute}}],{x:0.85,y:4.3,w:8.3,h:0.6,fontSize:11,fontFace:FONT_B,margin:0});
  addFooter(s,12);
})();

// SLIDE 13 — IMPLEMENTATION STATUS
(()=>{ const s=pres.addSlide(); s.background={color:C.bg}; slideTitle(s,"Implementation Status");
  const rows=[["Authentication and Authorization","Complete"],["Multi-tenant Schema Isolation","Complete"],["People, Roles, and Permissions","Complete"],["Organizational Units and Offerings","Complete"],["Cohorts, Groups, and Enrollment","Complete"],["Sessions, Schedule Periods, and Calendar","Complete"],["Assessment and Results","Complete"],["Credentials and Progression Rules","Complete"],["Resources and Room Booking","Complete"],["Financial: Invoices, Payments, Scholarships","Complete"],["Library Management System","Complete"],["Notifications and Workflow System","Complete"],["Stripe Payment Integration","In Progress"],["Template-specific School Seeding","Deferred"],["Self-service Password Recovery","Deferred"]];
  const ho={fill:{color:C.accent},color:C.white,bold:true,fontFace:FONT_H,fontSize:10,align:"center",valign:"middle"};
  const ro=(a)=>({fill:{color:a?C.card:C.bg},color:C.text,fontFace:FONT_B,fontSize:10,valign:"middle",border:{pt:0.5,color:C.line}});
  const so=(st,a)=>{let c=C.green;if(st==="In Progress")c=C.warm;if(st==="Deferred")c=C.mute;return{fill:{color:a?C.card:C.bg},color:c,fontFace:FONT_B,fontSize:10,bold:true,valign:"middle",border:{pt:0.5,color:C.line}};};
  let ist=[[{text:"Feature",options:ho},{text:"Status",options:ho}]];
  rows.forEach((r,i)=>ist.push([{text:r[0],options:ro(i%2===0)},{text:r[1],options:so(r[1],i%2===0)}]));
  s.addTable(ist,{x:0.6,y:1.0,w:8.8,colW:[7.0,1.8],rowH:[0.28,...rows.map(()=>0.26)],border:{pt:0.5,color:C.line}});
  addFooter(s,13);
})();

// SLIDE 14 — DEPENDENCIES USED (NEW)
(()=>{ const s=pres.addSlide(); s.background={color:C.bg}; slideTitle(s,"Dependencies Used");
  const hdr=t=>({text:t,options:{fill:{color:C.accent},color:C.white,bold:true,fontFace:FONT_H,fontSize:9,align:"center",valign:"middle"}});
  const cell=(t,a)=>({text:t,options:{fill:{color:a?C.card:C.bg},color:C.text,fontFace:FONT_B,fontSize:8.5,valign:"middle",border:{pt:0.5,color:C.line}}});
  const deps=[["Category","Dependency","Version","Purpose"],
    ["Framework","Spring Boot","3.5.14","Application framework, inversion of control"],
    ["Framework","Spring Security","3.5.14","Authentication and role-based access control"],
    ["Framework","Spring Data JPA","3.5.14","Object-relational mapping and repositories"],
    ["Framework","Spring Web (Tomcat)","10.1.54","Embedded HTTP server and REST endpoints"],
    ["Framework","Spring Actuator","3.5.14","Health checks and monitoring endpoints"],
    ["Framework","Spring Validation","3.5.14","Request DTO validation"],
    ["Auth","JJWT (io.jsonwebtoken)","0.12.6","JWT token generation and verification"],
    ["Database","PostgreSQL JDBC Driver","—","Postgres connectivity with schema-per-tenant"],
    ["Database","HikariCP","(bundled)","High-performance JDBC connection pooling"],
    ["ORM","Hibernate ORM","6.6.49","Entity management, schema auto-detection"],
    ["JSON","Jackson","(bundled)","JSON serialization and deserialization"],
    ["Frontend","Lucide Icons","(CDN)","Icon library for the user interface"],
    ["Auth","Google Identity Services","(CDN)","OAuth 2.0 popup-based sign-in"],
    ["Build","Apache Maven (wrapper)","3.9.15","Dependency management and JAR packaging"],
    ["Runtime","Java (OpenJDK Temurin)","25.0.2","Runtime environment"],
    ["Database","PostgreSQL Server","18.3","Relational database management system"]];
  s.addTable(deps.map((r,i)=>r.map((c,j)=>i===0?hdr(c):cell(c,(i-1)%2===0))),{x:0.3,y:0.95,w:9.4,colW:[1.5,2.8,1.4,3.7],rowH:[0.28,...deps.slice(1).map(()=>0.25)],border:{pt:0.5,color:C.line}});
  addFooter(s,14);
})();

// SLIDE 15 — CONCLUSION
(()=>{ const s=pres.addSlide(); s.background={color:C.bg}; slideTitle(s,"Conclusion");
  card(s,0.6,1.0,8.8,2.8);
  ["Every learning institution can be described with fourteen universal concepts. The differences are in the labels and the rules, not in the underlying data structures.","The three primitives that determine whether a system is genuinely configurable are Role, Enrollment, and Schedule. These must be modelled as relationships and self-referential structures.","Scope discipline is essential. Supporting K-12 private schools and vocational institutes is achievable and defensible. Attempting to support every institution type simultaneously leads to combinatorial complexity.","The SchoolHub implementation demonstrates all fourteen primitives in working code, with twelve of fifteen planned features complete. Stripe payment integration is currently in progress."].forEach((pt,i)=>{
    s.addShape(pres.shapes.OVAL,{x:0.85,y:1.15+i*0.65,w:0.22,h:0.22,fill:{color:C.accent}});
    s.addText(pt,{x:1.2,y:1.05+i*0.65,w:7.9,h:0.55,fontSize:12,fontFace:FONT_B,color:C.text,margin:0,valign:"top"});
  });
  s.addText("Thank you.",{x:0.6,y:4.2,w:8.8,h:0.5,fontSize:20,fontFace:FONT_H,color:C.accent,align:"center"});
  addFooter(s,15);
})();

pres.writeFile({fileName:"C:/Users/Deus/Desktop/Springworld/SchoolHub refix/docs/School Management Software.pptx"}).then(()=>console.log("PPTX done")).catch(e=>{console.error(e);process.exit(1);});
