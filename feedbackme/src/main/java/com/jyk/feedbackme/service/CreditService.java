package com.jyk.feedbackme.service;

import com.jyk.feedbackme.domain.AppUser;
import com.jyk.feedbackme.domain.CreditLedger;
import com.jyk.feedbackme.domain.CreditLedgerType;
import com.jyk.feedbackme.domain.PaymentOrder;
import com.jyk.feedbackme.domain.UserCreditBalance;
import com.jyk.feedbackme.repository.CreditLedgerRepository;
import com.jyk.feedbackme.repository.UserCreditBalanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class CreditService {

    private static final int FREE_SIGNUP_CREDITS = 1;
    private static final int ANALYSIS_COST = 1;

    private final UserCreditBalanceRepository creditBalanceRepository;
    private final CreditLedgerRepository creditLedgerRepository;

    public CreditService(UserCreditBalanceRepository creditBalanceRepository, CreditLedgerRepository creditLedgerRepository) {
        this.creditBalanceRepository = creditBalanceRepository;
        this.creditLedgerRepository = creditLedgerRepository;
    }

    @Transactional
    public void grantSignupCredits(AppUser user) {
        UserCreditBalance balance = getOrCreateBalance(user);
        balance.grantFree(FREE_SIGNUP_CREDITS);
        creditLedgerRepository.save(CreditLedger.builder()
                .user(user)
                .type(CreditLedgerType.FREE_GRANT)
                .amount(FREE_SIGNUP_CREDITS)
                .memo("회원가입 무료 분석권")
                .build());
    }

    @Transactional
    public void grantSignupCreditsIfMissing(AppUser user) {
        if (creditBalanceRepository.findByUser(user).isPresent()) {
            return;
        }

        grantSignupCredits(user);
    }

    @Transactional
    public void useForAnalysis(AppUser user, Long feedbackHistoryId) {
        UserCreditBalance balance = getOrCreateBalance(user);
        balance.use(ANALYSIS_COST);
        creditLedgerRepository.save(CreditLedger.builder()
                .user(user)
                .type(CreditLedgerType.USE)
                .amount(-ANALYSIS_COST)
                .feedbackHistoryId(feedbackHistoryId)
                .memo("직무 적합도 분석 사용")
                .build());
    }

    @Transactional
    public void refundForFailedAnalysis(AppUser user, Long feedbackHistoryId) {
        if (creditLedgerRepository.existsByFeedbackHistoryIdAndType(feedbackHistoryId, CreditLedgerType.REFUND)) {
            return;
        }

        UserCreditBalance balance = getOrCreateBalance(user);
        balance.refund(ANALYSIS_COST);
        creditLedgerRepository.save(CreditLedger.builder()
                .user(user)
                .type(CreditLedgerType.REFUND)
                .amount(ANALYSIS_COST)
                .feedbackHistoryId(feedbackHistoryId)
                .memo("분석 실패 환급")
                .build());
    }

    @Transactional
    public void purchaseCredits(AppUser user, PaymentOrder order) {
        UserCreditBalance balance = getOrCreateBalance(user);
        balance.purchase(order.getCreditAmount());
        creditLedgerRepository.save(CreditLedger.builder()
                .user(user)
                .type(CreditLedgerType.PURCHASE)
                .amount(order.getCreditAmount())
                .paymentOrderId(order.getOrderId())
                .memo(order.getOrderName())
                .build());
    }

    @Transactional(readOnly = true)
    public boolean hasAnalysisCredit(AppUser user) {
        return creditBalanceRepository.findByUser(user)
                .map(balance -> balance.getBalance() >= ANALYSIS_COST)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSummary(AppUser user) {
        UserCreditBalance balance = creditBalanceRepository.findByUser(user)
                .orElseGet(() -> UserCreditBalance.builder().user(user).build());

        return Map.of(
                "balance", balance.getBalance(),
                "totalGranted", balance.getTotalGranted(),
                "totalPurchased", balance.getTotalPurchased(),
                "totalUsed", balance.getTotalUsed()
        );
    }

    @Transactional
    public UserCreditBalance getOrCreateBalance(AppUser user) {
        return creditBalanceRepository.findByUser(user)
                .orElseGet(() -> creditBalanceRepository.save(UserCreditBalance.builder().user(user).build()));
    }
}
