import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {Store,Photos} from '../main/storage.mjs';
import {ChatService,decode,context} from '../main/chat.mjs';
import {MemoryStore,MemoryVault} from './helpers.mjs';
test('atomic store reopens complete state and recovers a corrupted primary from backup',()=>{
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'xq-storage-'));try{const store=new Store(root);store.change(s=>{s.drafts.test={body:'长文'};});store.save();fs.writeFileSync(path.join(root,'state.json'),'{broken');const restored=new Store(root);assert.equal(restored.state.drafts.test.body,'长文');assert.ok(restored.state.recovery);assert.ok(fs.readdirSync(root).some(f=>f.startsWith('state-unreadable-')));const photos=new Photos(root,restored);assert.throws(()=>photos.file('../secret'));}finally{fs.rmSync(root,{recursive:true,force:true});}
});
test('empty responses retry within the same message and proposal requires user save',async()=>{
 const store=new MemoryStore(),vault=new MemoryVault();await vault.set('deepseek','secret');let attempts=0;const transport=async()=>new Response(JSON.stringify({choices:[{finish_reason:'stop',message:{content:++attempts<3?'':'你好'}}]}));const chat=new ChatService(store,vault,{transport});const id=chat.create();await chat.send({sessionId:id,text:'聊天'});assert.equal(attempts,3);assert.equal(store.state.chats[0].history.length,2);assert.equal(store.state.data.events.length,0);
 const decoded=decode({choices:[{finish_reason:'tool_calls',message:{tool_calls:[{function:{name:'propose_records',arguments:JSON.stringify({actions:[{kind:'event',title:'AI 建议',date:'2026-11-25',allDay:true}]})}}]}}]});store.change(s=>s.chats[0].history.push({id:'proposal',role:'assistant',...decoded}));const ref={sessionId:id,messageId:'proposal',actionId:decoded.actions[0].id};const prepared=chat.prepare(ref);assert.equal(store.state.data.events.length,0);chat.saveProposal(ref,prepared.value);assert.equal(store.state.data.events.length,1);assert.throws(()=>chat.saveProposal(ref,prepared.value));
});
test('separate conversations and disabled context do not expose app records or keys',async()=>{
 const store=new MemoryStore(),vault=new MemoryVault();await vault.set('deepseek','secret-api');store.state.data.moods=[{id:'private',date:'2026-11-25',time:'12:00',body:'PRIVATE_DIARY',photos:['private.jpg']}];let request;const chat=new ChatService(store,vault,{transport:async(_u,o)=>{request=JSON.parse(o.body);return new Response(JSON.stringify({choices:[{finish_reason:'stop',message:{content:'收到'}}]}));}});const a=chat.create(),b=chat.create();await chat.send({sessionId:a,text:'只有A'});await chat.send({sessionId:b,text:'只有B'});const body=JSON.stringify(request);assert.ok(!body.includes('PRIVATE_DIARY'));assert.ok(!body.includes('只有A'));assert.ok(!body.includes('secret-api'));assert.throws(()=>context(store.state.data,'2026-01-01','2026-11-25'),/31/);
});
