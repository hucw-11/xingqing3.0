import {test} from 'node:test';
import assert from 'node:assert/strict';
import {SyncEngine,merge,records} from '../core/sync.mjs';
import {clone} from '../core/model.mjs';
import {MemoryStore,MemoryVault,MemoryPhotos,FakeCloud} from './helpers.mjs';
const device=cloud=>{const store=new MemoryStore(),vault=new MemoryVault(),photos=new MemoryPhotos(),engine=new SyncEngine(store,vault,photos,{clientFactory:()=>cloud.client()});return {store,vault,photos,engine};};
const event={id:'android-uuid',title:'来自手机的任务',date:'2026-11-25',allDay:true,repeat:'none',done:false,rollover:true,continuous:true,children:[{id:'child-uuid',title:'读书',done:false,focusDate:'2026-11-25'}],deadline:'2026-12-01'};
test('Android record keys and unknown fields survive two desktop devices and tombstones',async()=>{
 const cloud=new FakeCloud(),a=device(cloud),b=device(cloud);a.store.data({...a.store.state.data,events:[{...event,mobileFutureField:'preserve'}]});await a.engine.login('test-env-123','name','password');await b.engine.login('test-env-123','name','password');assert.equal(b.store.state.data.events[0].mobileFutureField,'preserve');assert.ok(cloud.rows.has('events:android-uuid'));
 b.store.data({...b.store.state.data,events:[]});await b.engine.run();await a.engine.run();assert.equal(a.store.state.data.events.length,0);assert.equal(cloud.rows.get('events:android-uuid').deleted,true);
 assert.ok(!JSON.stringify(a.store.state).includes('private-access'));assert.equal((await a.vault.get('cloud')).access_token,'private-access');
});
test('offline different fields merge; same-field diary conflicts require explicit resolution',async()=>{
 const cloud=new FakeCloud(),a=device(cloud),b=device(cloud);a.store.data({...a.store.state.data,events:[event],moods:[{id:'m',date:'2026-11-25',time:'12:00',body:'原文',mood:2,energy:2,photos:[]}]});await a.engine.login('test-env-123','name','password');await b.engine.login('test-env-123','name','password');
 for(const [d,title,note,body]of [[a,'电脑编辑',undefined,'电脑日记'],[b,undefined,'手机备注','手机日记']]){const next=clone(d.store.state.data);if(title)next.events[0].title=title;if(note)next.events[0].note=note;next.moods[0].body=body;d.store.data(next);}
 await a.engine.run();await b.engine.run();await a.engine.run();assert.equal(a.store.state.data.events[0].title,'电脑编辑');assert.equal(a.store.state.data.events[0].note,'手机备注');assert.ok(b.store.state.rows['moods:m'].conflict);assert.equal(b.store.state.data.moods[0].body,'手机日记');
 await b.engine.resolve('moods:m',false);assert.equal(b.store.state.data.moods[0].body,'电脑日记');assert.equal(b.store.state.rows['moods:m'].conflict,null);
});
test('lost network acknowledgment is idempotent and keeps edits made during send',async()=>{
 const cloud=new FakeCloud(),a=device(cloud);await a.engine.login('test-env-123','name','password');a.store.data({...a.store.state.data,events:[event]});cloud.lostAck=true;await a.engine.run();const revision=cloud.revision;assert.ok(a.store.state.rows['events:android-uuid'].dirty);await a.engine.run();assert.equal(cloud.revision,revision);assert.equal(a.store.state.rows['events:android-uuid'].dirty,false);
});
test('private photos upload once and reach the second device, refresh never stores password',async()=>{
 const cloud=new FakeCloud(),a=device(cloud),b=device(cloud);a.photos.write('mood.jpg',new Uint8Array([1,2,3]));a.store.data({...a.store.state.data,moods:[{id:'photo',date:'2026-11-25',time:'12:00',body:'图片',mood:2,energy:2,photos:['mood.jpg']}]});await a.engine.login('test-env-123','name','password');await b.engine.login('test-env-123','name','password');assert.deepEqual(b.photos.read('mood.jpg'),new Uint8Array([1,2,3]));assert.equal(cloud.images.size,1);
 await a.vault.set('cloud',{...(await a.vault.get('cloud')),expires_at:0});await a.engine.run();assert.ok(cloud.requests.some(([u])=>u.endsWith('/auth/v1/token')));assert.ok(!JSON.stringify(a.store.state).includes('password'));
});
test('array merge preserves independent child changes and union of progress logs',()=>{
 const base={...event,progressLog:{}},a=clone(base),b=clone(base);a.children[0].done=true;a.progressLog.one={id:'one',date:'2026-11-25'};b.children.push({id:'two',title:'实验',done:false});b.progressLog.two={id:'two',date:'2026-11-26'};const out=merge(base,a,b);assert.equal(out.conflict,false);assert.equal(out.value.children.length,2);assert.equal(out.value.children[0].done,true);assert.equal(Object.keys(out.value.progressLog).length,2);assert.deepEqual(Object.keys(records({events:[event]})),['events:android-uuid']);
});
