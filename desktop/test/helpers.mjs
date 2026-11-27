import {clone} from '../core/model.mjs';
import {initialState,capture,CloudClient} from '../core/sync.mjs';
export class MemoryStore {
 constructor(){this.state=initialState();this.backups=[];}
 change(fn){const next=clone(this.state);fn(next);this.state=next;}
 data(next){this.change(s=>{s.data=next;if(s.sync.enabled)capture(s);});}
 backup(name){this.backups.push([name,clone(this.state)]);}
}
export class MemoryVault {data={};async get(k){return clone(this.data[k]);}async set(k,v){this.data[k]=clone(v);}}
export class MemoryPhotos {constructor(store){this.store=store;}files=new Map();markers=new Set();exists(n){return this.files.has(n);}write(n,b){this.files.set(n,new Uint8Array(b));}read(n){return this.files.get(n);}uploaded(k){return this.store?!!this.store.state.uploaded?.[k]:this.markers.has(k);}mark(k){if(this.store)this.store.change(s=>{s.uploaded??={};s.uploaded[k]=true;});else this.markers.add(k);}}
export class FakeCloud {
 rows=new Map();images=new Map();revision=0;lostAck=false;requests=[];
 client(){const request=async(url,opts)=>{this.requests.push([url,opts]);const path=new URL(url).pathname,body=opts.body instanceof Uint8Array?opts.body:opts.body?JSON.parse(opts.body):null;let result;
  if(path.endsWith('/signin')||path.endsWith('/token'))result={sub:'phone-owner',access_token:'private-access',refresh_token:'private-refresh',expires_in:3600};
  else if(path.includes('/storages/')){if(opts.method==='POST'){this.images.set(path,opts.body);return new Response(null,{status:200});}return this.images.has(path)?new Response(this.images.get(path)):new Response(null,{status:404});}
  else if(path.endsWith('/xq_sync_records')){const cursor=Number(new URL(url).searchParams.get('revision').slice(3));result=[...this.rows.values()].filter(r=>r.revision>cursor).sort((a,b)=>a.revision-b.revision).slice(0,100);}
  else if(path.endsWith('/xq_sync_put')){let current=this.rows.get(body.p_key);if(current?.mutation_id===body.p_mutation)result={accepted:true,record:current};else if((current?.revision||0)!==body.p_expected)result={accepted:false,record:current};else{current={record_key:body.p_key,payload:body.p_payload,deleted:body.p_deleted,revision:++this.revision,mutation_id:body.p_mutation};this.rows.set(body.p_key,clone(current));result={accepted:true,record:current};if(this.lostAck){this.lostAck=false;throw Error('connection lost after commit');}}}
  else throw Error('unexpected endpoint '+path);return new Response(JSON.stringify(result),{headers:{'content-type':'application/json'}});
 };return new CloudClient('test-env-123','test-device',request);}
}
