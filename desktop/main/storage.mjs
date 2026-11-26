import fs from 'node:fs';
import path from 'node:path';
import {initialState,capture} from '../core/sync.mjs';
import {clone} from '../core/model.mjs';

export class Store {
 constructor(root){this.root=root;fs.mkdirSync(root,{recursive:true});this.file=path.join(root,'state.json');if(fs.existsSync(this.file)){try{this.state=JSON.parse(fs.readFileSync(this.file,'utf8'));}catch{const broken=path.join(root,`state-unreadable-${Date.now()}.json`);fs.copyFileSync(this.file,broken);if(!fs.existsSync(this.file+'.bak'))throw Error('本机数据读取失败，原文件已保留');this.state=JSON.parse(fs.readFileSync(this.file+'.bak','utf8'));this.state.recovery='上次本机文件异常，已恢复上一份完整备份';}}else this.state=initialState();if(this.state.schema!==1||!this.state.data||!this.state.rows)throw Error('不支持的本机数据格式');this.save();}
 save(){const temp=this.file+'.tmp',fd=fs.openSync(temp,'w');try{fs.writeFileSync(fd,JSON.stringify(this.state));fs.fsyncSync(fd);}finally{fs.closeSync(fd);}if(fs.existsSync(this.file))fs.copyFileSync(this.file,this.file+'.bak');fs.renameSync(temp,this.file);}
 change(fn){const next=clone(this.state);const value=fn(next);const old=this.state;this.state=next;try{this.save();}catch(e){this.state=old;throw e;}return value;}
 data(next){this.change(s=>{s.data=next;if(s.sync.enabled)capture(s);});}
 backup(prefix){const dir=path.join(this.root,'backups');fs.mkdirSync(dir,{recursive:true});fs.writeFileSync(path.join(dir,`${prefix}-${Date.now()}.json`),JSON.stringify(this.state));}
}
export class Photos {
 constructor(root,store){this.root=path.join(root,'photos');this.store=store;fs.mkdirSync(this.root,{recursive:true});}
 file(name){if(!/^[A-Za-z0-9._-]{1,160}$/.test(name)||name.includes('..'))throw Error('照片名称不正确');return path.join(this.root,name);}
 exists(name){return fs.existsSync(this.file(name));}
 read(name){const f=this.file(name);if(fs.statSync(f).size>16*1024*1024)throw Error('照片过大');return new Uint8Array(fs.readFileSync(f));}
 write(name,bytes){if(bytes.length>16*1024*1024)throw Error('照片过大');const file=this.file(name);fs.writeFileSync(file+'.tmp',bytes);fs.renameSync(file+'.tmp',file);}
 uploaded(key){return !!this.store.state.uploaded?.[key];}
 mark(key){this.store.change(s=>{s.uploaded=s.uploaded||{};s.uploaded[key]=true;});}
}
export class Vault {
 constructor(root,safeStorage){this.file=path.join(root,'credentials.enc');this.safe=safeStorage;this.tail=Promise.resolve();}
 enqueue(fn){const result=this.tail.then(fn);this.tail=result.catch(()=>{});return result;}
 async read(){if(!fs.existsSync(this.file))return {};if(!await this.safe.isAsyncEncryptionAvailable())throw Error('Windows 凭证加密暂不可用');const decrypted=await this.safe.decryptStringAsync(fs.readFileSync(this.file));const data=JSON.parse(decrypted.result);if(decrypted.shouldReEncrypt)await this.write(data);return data;}
 async write(data){if(!await this.safe.isAsyncEncryptionAvailable())throw Error('无法安全保存登录凭证');const bytes=await this.safe.encryptStringAsync(JSON.stringify(data));fs.writeFileSync(this.file+'.tmp',bytes);fs.renameSync(this.file+'.tmp',this.file);}
 get(key){return this.enqueue(async()=> (await this.read())[key]??null);}
 set(key,value){return this.enqueue(async()=>{const data=await this.read();if(value===null)delete data[key];else data[key]=value;await this.write(data);});}
}
