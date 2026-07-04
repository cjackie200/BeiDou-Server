# 元素武器规则

本文记录中文服元素杖当前规则，避免服务端 WZ、客户端 Data 和 `ijl15` DLL 之间出现双算或口径不一致。

## 当前倍率

- 元素杖只保留一个主属性加成，主属性 `incRMA* = 200`，表示对应元素技能按 `200%` 计算。
- 副属性加成已取消，不再保留 `110%` 副属性。
- `elemDefault = 50`，表示元素杖遇到非主属性元素技能时按 `50%` 计算。
- 中立或无属性技能不应因为元素杖受到惩罚，保持原始 `100%`。

## 字段对应

- 火属性：`incRMAF`
- 毒属性：`incRMAS`
- 冰属性：`incRMAI`
- 雷属性：`incRMAL`
- 未匹配元素倍率：`elemDefault`

当前客户端原始 Data 和服务端 WZ 的元素杖节点必须同向维护：

- `gms-server/wz/Character.wz/Weapon/01372035..01372042.img.xml`
- `gms-server/wz/Character.wz/Weapon/01382045..01382052.img.xml`
- `BeiDou-Client/Data/Character/Weapon/01372035..01372042.img`
- `BeiDou-Client/Data/Character/Weapon/01382045..01382052.img`

每个元素杖节点只应存在一个 `incRMA* = 200` 和一个 `elemDefault = 50`。

## 客户端与 DLL

客户端原始 WZ/Data 负责最终元素倍率计算。`ijl15` 只接收服务端下发的元素配置并记录诊断日志，不再额外改写 `MAGIC_ATTACK` 发包伤害，也不在 `CalcDamage::MDamage` 中叠加倍率。

这样可以避免匹配属性被原始客户端和 DLL 双重放大，同时保留原始客户端对 `elemDefault` 的惩罚行为。

## 服务端校验

服务端伤害校验从装备 WZ 读取当前 `incRMA*` 主属性倍率，用于放宽对应元素技能的最大伤害校验。非主属性技能不额外放宽；客户端如果因 `elemDefault=50` 发出较低伤害，仍低于服务端上限，不需要服务端单独乘 `50%`。
