const {contextBridge,ipcRenderer}=require('electron');
const allowed=new Set(['state','event.save','event.remove','event.toggle','child.edit','project.convert','course.save','course.remove','mood.save','mood.remove','settings.save','label.save','label.remove','sync.login','sync.logout','sync.run','sync.resolve','photos.pick','photos.get','file.export','file.import','backup.export','backup.restore','draft.save','prefs.save','chat.create','chat.select','chat.rename','chat.delete','chat.config','chat.exclude','chat.send','chat.stop','chat.proposal','chat.saveProposal','chat.ignore','ai.key']);
contextBridge.exposeInMainWorld('xingqing',{
 action:(name,payload={})=>{if(!allowed.has(name))return Promise.reject(Error('不支持的操作'));return ipcRenderer.invoke('xingqing:action',name,payload).then(result=>{if(!result.ok)throw Error(result.error);return result.value;});},
 onChange:callback=>{const listener=()=>callback();ipcRenderer.on('xingqing:changed',listener);return ()=>ipcRenderer.removeListener('xingqing:changed',listener);}
});
