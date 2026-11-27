import {clone,dayNo,minutes,agenda} from './model.mjs';
export const sections=['all','morning','afternoon','evening','time'];
export const names=['全天','上午','下午','晚上','具体时间'];
export const ref=e=>({kind:e.type==='child'?'event':e.type,id:e.source.id,occurrence:['event','child'].includes(e.type)?e.source.occurrence||e.source.date:'',childId:e.childId||''});
export const key=r=>`${r.occurrence||''}@${r.childId||'self'}`;
export const identity=r=>`${r.kind}:${r.id}:${key(r)}`;
export const group=e=>e.allDay?0:e.minute<720?1:e.minute<1080?2:3;
export function get(data,r,date){return data[({event:'events',course:'courses',mood:'moods'})[r.kind]]?.find(o=>o.id===r.id)?.agendaPlans?.[date]?.[key(r)]||{};}
export function set(data,r,date,plan){dayNo(date);const next=clone(data),record=next[({event:'events',course:'courses',mood:'moods'})[r.kind]]?.find(o=>o.id===r.id);if(!record)throw Error('安排已不存在');if(plan.section&&!sections.includes(plan.section))throw Error('时段不正确');if(plan.section==='time')minutes(plan.time);if(plan.order!==undefined&&!Number.isFinite(plan.order))throw Error('顺序不正确');if(r.kind==='course'||r.kind==='mood'){if(plan.section)throw Error('课程和心情的时间请在原记录中修改');}record.agendaPlans??={};record.agendaPlans[date]??={};record.agendaPlans[date][key(r)]={...get(next,r,date),...clone(plan)};return next;}
export function arrange(data,rows,date){const out=[];for(const e of rows){const p=get(data,ref(e),date);if(e.continuous&&p.section)out.push(apply({...e,continuous:false},p));out.push(e.continuous?e:apply(e,p));}return out.sort((a,b)=>Number(a.done)-Number(b.done)||group(a)-group(b)||((get(data,ref(a),date).order??a.minute*1024)-(get(data,ref(b),date).order??b.minute*1024))||a.title.localeCompare(b.title));}
function apply(e,p){if(!p.section)return e;const n=sections.indexOf(p.section);return {...e,allDay:n===0,minute:n===0?-1:n===4?minutes(p.time):[0,0,720,1080][n],time:n===4?p.time:names[n]};}
export function reorder(data,date,refs){const rows=agenda(data,date).filter(e=>!e.done&&!e.continuous),ids=refs.map(identity);if(new Set(ids).size!==ids.length)throw Error('安排重复');const selected=ids.map(id=>rows.find(e=>identity(ref(e))===id));if(selected.some(e=>!e)||new Set(selected.map(group)).size!==1)throw Error('只能在同一时段内排序');let next=data;for(let i=0;i<refs.length;i++)next=set(next,refs[i],date,{order:i*1024});return next;}
