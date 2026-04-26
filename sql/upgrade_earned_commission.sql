-- Add earned_commission column to merchant_follow
-- earned_commission = actual commission credited to this salesman after admin approval
-- (may differ from commission which is the cooperation amount entered by salesman)
ALTER TABLE merchant_follow
    ADD COLUMN earned_commission DECIMAL(10, 2) NULL COMMENT '实际到账佣金（审批通过时写入）' AFTER commission;
