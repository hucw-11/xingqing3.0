import {test} from 'node:test';
import assert from 'node:assert/strict';
import * as M from '../core/model.mjs';
const day='2026-11-25', now=new Date(day+'T13:00:00');
const base=()=>({...M.emptyData(day),configured:true,semester:'2026-11-23'});
const task=(extra={})=>({id:'parent',title:'本周研究',date:'2026-11-23',allDay:true,start:'14:00',repeat:'none',rollover:true,continuous:true,done:false,children:[{id:'a',title:'阅读',done:false},{id:'b',title:'实验',done:false}],...extra});

test('one course on Tuesday and Thursday occupies two periods and generates exact agenda times',()=>{
 const data=base(),c=M.validateCourse({title:'现代导航技术',days:[2,4],from:3,to:4,weeks:'1-16',room:'A302'},data);data.courses=[c];
 for(const d of ['2026-11-24','2026-11-26']){const rows=M.agenda(data,d,now);assert.equal(rows.length,1);assert.equal(rows[0].time,'09:50–11:25');assert.equal(rows[0].note,'A302');}
 assert.equal(M.agenda(data,day,now).length,0);assert.throws(()=>M.validateCourse({...c,id:'duplicate'},data),/冲突/);
});
test('completed courses move below incomplete tasks without expiring timed todos',()=>{
 const data=base();data.courses=[{id:'c',title:'早课',days:[3],from:1,to:2,weeks:'1'}];data.events=[task({continuous:false,date:day,allDay:false,start:'09:00'})];
 const rows=M.agenda(data,day,now);assert.equal(rows[0].type,'event');assert.equal(rows[0].done,false);assert.equal(rows[1].done,true);
});
test('focus is the same child, carries to today, and completion retains the mother progress',()=>{
 let data=base();data.events=[task()];data=M.editChild(data,'parent','2026-11-23','a','focus',new Date('2026-11-24T09:00:00'));
 assert.equal(M.agenda(data,day,now).filter(r=>r.type==='child').length,1);
 data=M.editChild(data,'parent','2026-11-23','a','done',now);const event=data.events[0];assert.equal(event.children[0].completedDate,day);assert.equal(event.done,false);assert.equal(data.events.length,1);
 assert.equal(M.agenda(data,'2026-11-24',now).length,0);
 const tomorrow=M.agenda(data,'2026-11-26',new Date('2026-11-26T10:00:00'));assert.equal(tomorrow.filter(r=>r.type==='child').length,0);
 const history=M.agenda(data,day,new Date('2026-11-26T10:00:00'));assert.equal(history.length,1);assert.equal(history[0].type,'progress');assert.match(history[0].note,/阅读/);
});
test('parent completed after rollover belongs only to actual completion day',()=>{
 let data=base();data.events=[task()];data=M.toggleTask(data,'parent','2026-11-23',true,now);assert.equal(data.events[0].completedDate,day);assert.ok(data.events[0].children.every(c=>c.done));
 assert.equal(M.agenda(data,'2026-11-23',now).filter(r=>r.type==='event').length,0);assert.equal(M.agenda(data,day,now).filter(r=>r.done).length,1);assert.equal(M.agenda(data,'2026-11-26',new Date('2026-11-26T10:00:00')).length,0);
});
test('weekly instances retain independent child states and one deletion is a tombstone',()=>{
 let data=base();data.events=[task({continuous:false,repeat:'weekly',date:'2026-11-24',weekdays:[2,4],count:4})];
 assert.deepEqual([...M.dates(data.events[0],'2026-12-30')],['2026-11-24','2026-11-26','2026-12-01','2026-12-03']);
 data=M.editChild(data,'parent','2026-11-24','a','done',now);assert.equal(M.occurrence(data.events[0],'2026-11-24').children[0].done,true);assert.equal(M.occurrence(data.events[0],'2026-11-26').children[0].done,false);
 data=M.saveEvent(data,null,{id:'parent',day:'2026-11-26',remove:true});assert.equal(M.occurrence(data.events[0],'2026-11-26'),null);assert.ok(M.occurrence(data.events[0],'2026-12-01'));
});
test('future edit detaches completed occurrences and does not duplicate them in new series',()=>{
 let data=base();data.events=[task({continuous:false,repeat:'daily',date:day,count:5})];data=M.toggleTask(data,'parent','2026-11-27',true,now);
 data=M.saveEvent(data,{...M.occurrence(data.events[0],'2026-11-26'),title:'新计划',count:4},{id:'parent',day:'2026-11-26',scope:'future'},now);
 assert.equal(data.events.length,3);assert.equal(data.events[0].until,day);assert.equal(data.events.filter(e=>e.done&&e.repeat==='none').length,1);assert.equal(M.occurrence(data.events.at(-1),'2026-11-27'),null);
});
test('long diary, optional title, five levels and validation',()=>{
 const mood=M.validateMood({date:day,time:'19:30',body:'长篇想法。'.repeat(5000),mood:0,energy:4,photos:[]});assert.ok(mood.id);assert.ok(mood.body.length>20000);assert.throws(()=>M.validateMood({...mood,mood:5}));assert.throws(()=>M.validateMood({...mood,title:'',body:'',photos:[]}));
 assert.throws(()=>M.dayNo('2026-02-30'));assert.throws(()=>M.validateSettings({...base(),ends:['07:00',...M.ends.slice(1)]},base()));
});
test('deleting tags removes references inside occurrence overrides',()=>{const data=base();data.events=[task({tags:['desktop-study'],overrides:{[day]:{tags:['desktop-study']}}})];const next=M.unlinkLabel(data,'desktop-study');assert.deepEqual(next.events[0].overrides[day].tags,[]);assert.equal(next.labels.length,3);});
