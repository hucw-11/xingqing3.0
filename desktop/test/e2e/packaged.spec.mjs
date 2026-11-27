import {test,expect,_electron as electron} from '@playwright/test';
import path from 'node:path';
test('packaged installed executable launches with secure bridge',async()=>{
 test.skip(!process.env.XINGQING_PACKAGED_EXE,'Runs after installer build');
 const app=await electron.launch({executablePath:process.env.XINGQING_PACKAGED_EXE,env:{...process.env,APPDATA:path.resolve('test-results/packaged-profile')}});
 try{const page=await app.firstWindow();await page.setViewportSize({width:1360,height:860});await expect(page.locator('#version')).toContainText('1.1.0');expect(await page.evaluate(()=>typeof window.require)).toBe('undefined');await page.locator('#new-mood').click();await page.getByLabel('写下此刻的想法',{exact:true}).fill('安装版运行正常');await page.locator('#inspector').getByRole('button',{name:'保存',exact:true}).click();await expect(page.locator('.entry[data-title="此刻的心情"]')).toBeVisible();await page.screenshot({path:'test-results/screenshots/installed-app.png'});}finally{await app.close();}
});
