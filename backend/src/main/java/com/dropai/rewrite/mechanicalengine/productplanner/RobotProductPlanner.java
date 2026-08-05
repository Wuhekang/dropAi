package com.dropai.rewrite.mechanicalengine.productplanner;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalDesignSpec;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class RobotProductPlanner extends AbstractCatalogProductPlanner {
    public ProductFamily family() { return ProductFamily.ROBOT; }

    public MechanicalDesignSpec plan(String requirement) {
        String text = requirement == null ? "" : requirement.toLowerCase(Locale.ROOT);
        return contains(text, "油罐", "爬壁", "履带", "磁吸", "wall-climbing", "crawler", "tank inspection")
                ? tankRobot() : mobileRobot();
    }

    private MechanicalDesignSpec tankRobot() {
        List<MechanicalDesignSpec.Module> modules = List.of(
                module("M01","承载机架系统","承载并定位全部机械系统",List.of("履带基准","磁吸导轨","设备平台"),"螺栓连接铝合金框架"),
                module("M02","左履带行走系统","提供左侧牵引和越障能力",List.of("驱动轴","张紧槽","履带导向"),"可拆卸侧置总成"),
                module("M03","右履带行走系统","提供右侧牵引和差速转向",List.of("驱动轴","张紧槽","履带导向"),"可拆卸侧置总成"),
                module("M04","永磁吸附系统","在曲面钢制罐壁上提供失效安全吸附",List.of("气隙调节器","磁钢座","机架导轨"),"底部可调导轨安装"),
                module("M05","旋转清扫系统","清除检测路径上的锈蚀和污染物",List.of("刷盘轴","电机法兰","防护罩"),"前置快拆安装"),
                module("M06","检测调节系统","控制检测传感器提离值和位置",List.of("直线滑轨","传感器座","快拆销"),"后置浮动滑轨安装"),
                module("M07","驱动传动系统","将减速电机转矩传递至左右履带",List.of("减速电机","联轴器","传动轴","轴承座"),"左右对称密封安装"),
                module("M08","防护维护系统","保护电子设备和旋转部件",List.of("上盖","履带罩","线缆密封"),"密封可拆面板"));

        List<PartSeed> p = new ArrayList<>();
        add(p,"主承载底板","M01","形成整机主载荷路径","6061-T6","CNC加工",520,360,12,false,10,6,"FIXED",0);
        add(p,"前横梁","M01","承受清扫反力并连接左右履带","6061-T6","CNC加工",360,55,12,false,8,4,"FASTENED",0);
        add(p,"后横梁","M01","承受检测滑轨和履带载荷","6061-T6","CNC加工",360,55,12,false,8,4,"FASTENED",0);
        add(p,"电子设备平台","M01","安装控制器与电池箱","5052-H32","钣金折弯",300,230,3,false,6,2,"FASTENED",0);
        crawler(p,"左","M02",0);
        crawler(p,"右","M03",0);
        add(p,"前磁钢安装梁","M04","安装前排永磁体","Q235B","CNC加工",300,52,10,false,8,3,"FASTENED",0);
        add(p,"后磁钢安装梁","M04","安装后排永磁体","Q235B","CNC加工",300,52,10,false,8,3,"FASTENED",0);
        add(p,"前永磁体组件","M04","提供前部法向吸附力","NdFeB N42","磁体装配",45,30,12,false,5,2,"FASTENED",18);
        add(p,"后永磁体组件","M04","提供后部法向吸附力","NdFeB N42","磁体装配",45,30,12,false,5,2,"FASTENED",19);
        add(p,"磁隙调节滑座","M04","调节磁体与罐壁工作气隙","40Cr","CNC磨削",120,35,8,false,6,2,"COINCIDENT",0);
        add(p,"磁隙调节丝杆","M04","锁定磁吸气隙","40Cr","车削",18,18,150,true,6,1,"CONCENTRIC",22);
        add(p,"圆盘刷盘","M05","安装可更换钢丝刷束","45钢","车削",160,160,10,true,12,3,"CONCENTRIC",25);
        add(p,"清扫刷轴","M05","传递清扫电机转矩","40Cr","车削磨削",20,20,135,true,8,1,"CONCENTRIC",26);
        add(p,"清扫轴承座","M05","支撑刷轴轴承","6061-T6","CNC加工",90,70,32,false,10,3,"FASTENED",1);
        add(p,"清扫电机座","M05","同轴安装清扫电机","6061-T6","CNC加工",110,85,10,false,8,4,"FASTENED",1);
        add(p,"刷盘防护罩","M05","限制清扫碎屑飞散","5052-H32","钣金成形",190,170,2,false,5,2,"FASTENED",27);
        add(p,"传感器直线滑轨","M06","引导传感器径向调节","GCr15","采购直线导轨",180,20,12,false,5,1,"FASTENED",2);
        add(p,"传感器滑座","M06","沿滑轨承载检测探头","6061-T6","CNC加工",75,60,18,false,6,3,"COINCIDENT",30);
        add(p,"传感器快拆座","M06","定位超声或漏磁探头","POM","CNC加工",65,48,16,false,5,3,"FASTENED",31);
        add(p,"传感器快拆销","M06","实现探头无工具更换","304不锈钢","车削",8,8,55,true,4,1,"CONCENTRIC",32);
        add(p,"左减速电机座","M07","对准左电机和驱动轴","6061-T6","CNC加工",130,100,12,false,10,4,"FASTENED",0);
        add(p,"右减速电机座","M07","对准右电机和驱动轴","6061-T6","CNC加工",130,100,12,false,10,4,"FASTENED",0);
        add(p,"左驱动轴","M07","向左驱动轮传递转矩","40Cr","车削磨削",24,24,160,true,8,1,"CONCENTRIC",4);
        add(p,"右驱动轴","M07","向右驱动轮传递转矩","40Cr","车削磨削",24,24,160,true,8,1,"CONCENTRIC",11);
        add(p,"左驱动轴承座","M07","支撑左驱动轴径向载荷","6061-T6","CNC加工",75,65,25,false,12,3,"FASTENED",34);
        add(p,"右驱动轴承座","M07","支撑右驱动轴径向载荷","6061-T6","CNC加工",75,65,25,false,12,3,"FASTENED",35);
        add(p,"密封上盖","M08","保护控制器免受粉尘和碎屑影响","5052-H32","钣金折弯",330,250,2,false,5,2,"FASTENED",3);
        add(p,"左履带防护罩","M08","防止碎屑进入左履带","5052-H32","钣金折弯",430,95,2,false,5,2,"FASTENED",4);
        add(p,"右履带防护罩","M08","防止碎屑进入右履带","5052-H32","钣金折弯",430,95,2,false,5,2,"FASTENED",11);

        return build("wall_climbing_tank_inspection_robot","油罐检测爬壁机器人","清扫并检测曲面铁磁油罐壁面，同时保持失效安全吸附",
                "双履带差速行走，底置可调永磁体吸附，前置圆盘刷清扫，后置滑轨控制检测提离值",modules,p,
                List.of(parameter("robot_mass",38,"kg","满足两人搬运并控制壁面载荷"),parameter("rated_adhesion_force",1800,"N","按整机重力三倍以上确定"),
                        parameter("maximum_speed",0.25,"m/s","满足稳定检测采样"),parameter("minimum_tank_radius",2500,"mm","覆盖常见大型储罐曲率"),
                        parameter("magnet_air_gap",6,"mm","兼顾涂层间隙与吸附力"),parameter("brush_speed",900,"rpm","兼顾除锈效果和碎屑控制"),
                        parameter("design_safety_factor",3.0,"","垂直壁面运行要求失效安全")),
                List.of("整机重力","主承载底板","磁钢安装梁","永磁体","油罐壁"),
                List.of("减速电机","驱动轴","驱动轮","履带","罐壁移动"));
    }

    private void crawler(List<PartSeed> p,String side,String module,int frame) {
        add(p,side+"履带侧板",module,"定位履带轴系和支重轮","6061-T6","CNC加工",430,95,12,false,10,5,"FASTENED",frame);
        int plate=p.size()-1;
        add(p,side+"驱动轮",module,"驱动履带","40Cr","滚齿热处理",110,110,28,true,20,2,"CONCENTRIC",plate);
        add(p,side+"从动轮",module,"履带回程导向","40Cr","滚齿热处理",100,100,25,true,18,2,"CONCENTRIC",plate);
        add(p,side+"前支重轮",module,"支撑履带承载段","45钢","车削",55,55,35,true,12,2,"CONCENTRIC",plate);
        add(p,side+"后支重轮",module,"支撑履带承载段","45钢","车削",55,55,35,true,12,2,"CONCENTRIC",plate);
        add(p,side+"履带带体",module,"向罐壁传递牵引力","增强NBR","模压采购",390,70,18,false,8,3,"COINCIDENT",plate);
        add(p,side+"张紧滑块",module,"调节履带预紧力","40Cr","CNC加工",85,45,16,false,8,2,"COINCIDENT",plate);
    }

    private MechanicalDesignSpec mobileRobot() {
        return build("mobile_robot","Mobile inspection robot","carry sensors through an industrial inspection route","differential wheel drive moves a protected sensor platform",
                List.of(module("M01","Frame Module","support modules",List.of("drive mounts","sensor deck"),"bolted frame"),module("M02","Drive Module","provide motion",List.of("motor mounts","wheel axes"),"side installation"),module("M03","Sensor Module","position sensors",List.of("mast flange"),"bolted above frame"),module("M04","Power Module","retain battery",List.of("battery tray"),"low installation"),module("M05","Control Module","protect controller",List.of("sealed enclosure"),"isolated mounting")),
                List.of(new PartSeed("P001","Main frame","M01","carry load","6061-T6","CNC milling",650,480,12,false,10,6,"FIXED",0,"datum","world","fix frame"),new PartSeed("P002","Drive carrier","M02","support motors","6061-T6","CNC milling",260,120,14,false,10,4,"COINCIDENT",0,"face","datum","mount drive"),new PartSeed("P003","Sensor mast","M03","elevate sensors","6061-T6","CNC milling",120,100,420,false,8,5,"COINCIDENT",0,"base","top","mount mast"),new PartSeed("P004","Battery tray","M04","locate battery","5052-H32","sheet metal bending",300,220,3,false,6,2,"COINCIDENT",0,"bottom","deck","locate battery"),new PartSeed("P005","Control base","M05","mount controller","6061-T6","CNC milling",280,200,8,false,6,3,"COINCIDENT",0,"bottom","deck","mount controller")),
                List.of(parameter("robot_mass",45,"kg","portable target"),parameter("maximum_speed",0.8,"m/s","safe speed"),parameter("design_safety_factor",2,"","impact allowance")),List.of("payload","frame","wheels","floor"),List.of("motor","wheel","translation"));
    }

    private void add(List<PartSeed> p,String name,String module,String function,String material,String process,double length,double width,double height,boolean circular,double hole,double fillet,String relation,int parent) {
        p.add(new PartSeed("P"+String.format("%03d",p.size()+1),name,module,function,material,process,length,width,height,circular,hole,fillet,relation,parent,"assembly datum","parent datum","实现"+function));
    }
    private boolean contains(String value,String... keys){for(String key:keys)if(value.contains(key))return true;return false;}
}
