package com.traffic.device.service;

import com.traffic.common.BusinessException;
import com.traffic.common.ErrorCode;
import com.traffic.device.dto.PersonDetection;
import com.traffic.device.entity.TrafficFact;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 26维行人属性解析器
 *
 * 属性数组索引定义：
 *  [0]  帽子置信度（>0.5计1）
 *  [1]  眼镜置信度（>0.5计1）
 *  [2]  短袖置信度  \
 *  [3]  长袖置信度   > 互斥，取argmax决定上衣类型
 *  [10] 长外套置信度/
 *  [4]  上衣条纹（>0.5计1，可叠加）
 *  [5]  上衣Logo（>0.5计1，可叠加）
 *  [6]  上衣格子（>0.5计1，可叠加）
 *  [7]  上衣拼接（>0.5计1，可叠加）
 *  [8]  下装条纹（>0.5计1）
 *  [9]  下装图案（>0.5计1）
 *  [11] 长裤置信度  \
 *  [12] 短裤置信度   > 互斥，取argmax决定下装类型
 *  [13] 裙子置信度  /
 *  [14] 靴子（>0.5计1）
 *  [15] 手提包（>0.5计1）
 *  [16] 单肩包（>0.5计1）
 *  [17] 背包（>0.5计1）
 *  [18] 手持物品（>0.5计1）
 *  [19] <18岁置信度  \
 *  [20] 18-60岁置信度 > 互斥，取argmax决定年龄段
 *  [21] >60岁置信度  /
 *  [22] 女性置信度（>0.5=女，否则=男）
 *  [23-25] 预留
 */
@Slf4j
@Component
public class AttributeParser {

    private static final int EXPECTED_LENGTH = 26;
    private static final double THRESHOLD = 0.5;

    /**
     * 解析一条行人记录，将结果累加到对应的 TrafficFact 对象上
     *
     * @param detection 行人检测数据
     * @param fact      待累加的聚合桶（同一 merchant+device+分钟 的聚合对象）
     */
    public void parseAndAccumulate(PersonDetection detection, TrafficFact fact) {
        List<Double> attrs = detection.getAttributes();

        // 校验属性数组长度
        if (attrs == null || attrs.size() != EXPECTED_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_ATTRIBUTES_LENGTH);
        }

        // ---------- 穿行标记 ----------
        if (detection.isPassThrough()) {
            fact.setPassCount(fact.getPassCount() + 1);
            // 穿行者不计入其他维度分析，直接返回
            return;
        }

        // 非穿行，计入进店
        fact.setEnterCount(fact.getEnterCount() + 1);

        // ---------- 停留时长 ----------
        if (detection.getEntryTime() != null && detection.getExitTime() != null) {
            long staySeconds = Duration.between(detection.getEntryTime(), detection.getExitTime()).getSeconds();
            if (staySeconds > 0) {
                fact.setTotalStaySeconds(fact.getTotalStaySeconds() + (int) staySeconds);
                fact.setStayCount(fact.getStayCount() + 1);
                // 按区间分桶：<5min / 5-15min / >15min
                if (staySeconds < 300) {
                    fact.setStayUnder5min(fact.getStayUnder5min() + 1);
                } else if (staySeconds <= 900) {
                    fact.setStay5To15min(fact.getStay5To15min() + 1);
                } else {
                    fact.setStayOver15min(fact.getStayOver15min() + 1);
                }
            }
        }

        // ---------- 性别 [22] ----------
        // >0.5 为女性，否则为男性
        if (attrs.get(22) > THRESHOLD) {
            fact.setGenderFemale(fact.getGenderFemale() + 1);
        } else {
            fact.setGenderMale(fact.getGenderMale() + 1);
        }

        // ---------- 年龄段 [19, 20, 21] ----------
        // 互斥，取置信度最大的那一项
        int ageIdx = argmax(attrs, 19, 20, 21);
        switch (ageIdx) {
            case 19 -> fact.setAgeUnder18(fact.getAgeUnder18() + 1);
            case 20 -> fact.setAge1860(fact.getAge1860() + 1);
            case 21 -> fact.setAgeOver60(fact.getAgeOver60() + 1);
        }

        // ---------- 上衣类型 [2, 3, 10] ----------
        // 互斥，取置信度最大：短袖/长袖/长外套
        int upperIdx = argmax(attrs, 2, 3, 10);
        switch (upperIdx) {
            case 2  -> fact.setUpperShort(fact.getUpperShort() + 1);
            case 3  -> fact.setUpperLong(fact.getUpperLong() + 1);
            case 10 -> fact.setUpperCoat(fact.getUpperCoat() + 1);
        }

        // ---------- 上衣风格 [4, 5, 6, 7] ----------
        // 可叠加，各自>0.5则计1
        if (attrs.get(4) > THRESHOLD) fact.setUpperStyleStripe(fact.getUpperStyleStripe() + 1);
        if (attrs.get(5) > THRESHOLD) fact.setUpperStyleLogo(fact.getUpperStyleLogo() + 1);
        if (attrs.get(6) > THRESHOLD) fact.setUpperStylePlaid(fact.getUpperStylePlaid() + 1);
        if (attrs.get(7) > THRESHOLD) fact.setUpperStyleSplice(fact.getUpperStyleSplice() + 1);

        // ---------- 下装类型 [11, 12, 13] ----------
        // 互斥，取置信度最大：长裤/短裤/裙子
        int lowerIdx = argmax(attrs, 11, 12, 13);
        switch (lowerIdx) {
            case 11 -> fact.setLowerTrousers(fact.getLowerTrousers() + 1);
            case 12 -> fact.setLowerShorts(fact.getLowerShorts() + 1);
            case 13 -> fact.setLowerSkirt(fact.getLowerSkirt() + 1);
        }

        // ---------- 下装风格 [8, 9] ----------
        if (attrs.get(8) > THRESHOLD) fact.setLowerStyleStripe(fact.getLowerStyleStripe() + 1);
        if (attrs.get(9) > THRESHOLD) fact.setLowerStylePattern(fact.getLowerStylePattern() + 1);

        // ---------- 配饰 [0: 帽子, 1: 眼镜, 14: 靴子] ----------
        if (attrs.get(0) > THRESHOLD) fact.setAccessoryHat(fact.getAccessoryHat() + 1);
        if (attrs.get(1) > THRESHOLD) fact.setAccessoryGlasses(fact.getAccessoryGlasses() + 1);
        if (attrs.get(14) > THRESHOLD) fact.setAccessoryBoots(fact.getAccessoryBoots() + 1);

        // ---------- 包袋 [15: 手提包, 16: 单肩包, 17: 背包, 18: 手持物品] ----------
        if (attrs.get(15) > THRESHOLD) fact.setBagHandbag(fact.getBagHandbag() + 1);
        if (attrs.get(16) > THRESHOLD) fact.setBagShoulder(fact.getBagShoulder() + 1);
        if (attrs.get(17) > THRESHOLD) fact.setBagBackpack(fact.getBagBackpack() + 1);
        if (attrs.get(18) > THRESHOLD) fact.setHoldItem(fact.getHoldItem() + 1);
    }

    /**
     * 在给定索引中找置信度最大的那个索引
     * 例如 argmax(attrs, 19, 20, 21) 返回 19、20 或 21 中置信度最高的那个
     */
    private int argmax(List<Double> attrs, int... indices) {
        int maxIdx = indices[0];
        double maxVal = attrs.get(indices[0]);
        for (int i = 1; i < indices.length; i++) {
            double val = attrs.get(indices[i]);
            if (val > maxVal) {
                maxVal = val;
                maxIdx = indices[i];
            }
        }
        return maxIdx;
    }
}
