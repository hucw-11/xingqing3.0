package cn.campus.schedule;
final class DaySections {
    static int group(boolean allDay,int minute){return allDay?0:minute<720?1:minute<1080?2:3;}
}
