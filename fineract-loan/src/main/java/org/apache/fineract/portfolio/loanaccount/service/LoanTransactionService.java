/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.portfolio.loanaccount.service;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleInstallment;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepaymentScheduleProcessingWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionComparator;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoanTransactionService {

    public List<LoanTransaction> retrieveListOfTransactionsForReprocessing(final Loan loan) {
        return loan.getLoanTransactions().stream().filter(loanTransactionForReprocessingPredicate())
                .sorted(LoanTransactionComparator.INSTANCE).collect(Collectors.toList());
    }

    public LoanTransaction getLastTransactionForReprocessing(final Loan loan) {
        return loan.getLoanTransactions().stream() //
                .filter(loanTransactionForReprocessingPredicate()) //
                .reduce((first, second) -> second) //
                .orElse(null);
    }

    private Predicate<LoanTransaction> loanTransactionForReprocessingPredicate() {
        return transaction -> transaction.isNotReversed() && (transaction.isChargeOff() || transaction.isReAge()
                || transaction.isAccrualActivity() || transaction.isReAmortize() || !transaction.isNonMonetaryTransaction());
    }

    public Money getReceivableInterest(final Loan loan, final LocalDate tillDate) {
        Money receivableInterest = Money.zero(loan.getCurrency());
        for (final LoanTransaction transaction : loan.getLoanTransactions()) {
            if (transaction.isNotReversed() && !transaction.isRepaymentAtDisbursement() && !transaction.isDisbursement()
                    && !DateUtils.isAfter(transaction.getTransactionDate(), tillDate)) {
                if (transaction.isAccrual()) {
                    receivableInterest = receivableInterest.plus(transaction.getInterestPortion(loan.getCurrency()));
                } else if (transaction.isRepaymentLikeType() || transaction.isInterestWaiver() || transaction.isAccrualAdjustment()) {
                    receivableInterest = receivableInterest.minus(transaction.getInterestPortion(loan.getCurrency()));
                }
            }
            if (receivableInterest.isLessThanZero()) {
                receivableInterest = receivableInterest.zero();
            }
        }
        return receivableInterest;
    }

    public LoanRepaymentScheduleInstallment fetchLoanForeclosureDetail(final Loan loan, final LocalDate closureDate) {
        Money[] receivables = retrieveIncomeOutstandingTillDate(loan, closureDate);
        Money totalPrincipal = Money.of(loan.getCurrency(), loan.getSummary().getTotalPrincipalOutstanding());
        totalPrincipal = totalPrincipal.minus(receivables[3]);
        final LocalDate currentDate = DateUtils.getBusinessLocalDate();
        return new LoanRepaymentScheduleInstallment(null, 0, currentDate, currentDate, totalPrincipal.getAmount(),
                receivables[0].getAmount(), receivables[1].getAmount(), receivables[2].getAmount(), false, null);
    }

    private Money[] retrieveIncomeOutstandingTillDate(final Loan loan, final LocalDate paymentDate) {
        Money[] balances = new Money[4];
        final MonetaryCurrency currency = loan.getCurrency();
        Money interest = Money.zero(currency);
        Money paidFromFutureInstallments = Money.zero(currency);
        Money fee = Money.zero(currency);
        Money penalty = Money.zero(currency);
        int firstNormalInstallmentNumber = LoanRepaymentScheduleProcessingWrapper
                .fetchFirstNormalInstallmentNumber(loan.getRepaymentScheduleInstallments());

        for (final LoanRepaymentScheduleInstallment installment : loan.getRepaymentScheduleInstallments()) {
            boolean isFirstNormalInstallment = installment.getInstallmentNumber().equals(firstNormalInstallmentNumber);
            if (!DateUtils.isBefore(paymentDate, installment.getDueDate())) {
                interest = interest.plus(installment.getInterestOutstanding(currency));
                penalty = penalty.plus(installment.getPenaltyChargesOutstanding(currency));
                fee = fee.plus(installment.getFeeChargesOutstanding(currency));
            } else if (DateUtils.isAfter(paymentDate, installment.getFromDate())) {
                Money[] balancesForCurrentPeroid = loan.fetchInterestFeeAndPenaltyTillDate(paymentDate, currency, installment,
                        isFirstNormalInstallment);
                if (balancesForCurrentPeroid[0].isGreaterThan(balancesForCurrentPeroid[5])) {
                    interest = interest.plus(balancesForCurrentPeroid[0]).minus(balancesForCurrentPeroid[5]);
                } else {
                    paidFromFutureInstallments = paidFromFutureInstallments.plus(balancesForCurrentPeroid[5])
                            .minus(balancesForCurrentPeroid[0]);
                }
                if (balancesForCurrentPeroid[1].isGreaterThan(balancesForCurrentPeroid[3])) {
                    fee = fee.plus(balancesForCurrentPeroid[1].minus(balancesForCurrentPeroid[3]));
                } else {
                    paidFromFutureInstallments = paidFromFutureInstallments
                            .plus(balancesForCurrentPeroid[3].minus(balancesForCurrentPeroid[1]));
                }
                if (balancesForCurrentPeroid[2].isGreaterThan(balancesForCurrentPeroid[4])) {
                    penalty = penalty.plus(balancesForCurrentPeroid[2].minus(balancesForCurrentPeroid[4]));
                } else {
                    paidFromFutureInstallments = paidFromFutureInstallments.plus(balancesForCurrentPeroid[4])
                            .minus(balancesForCurrentPeroid[2]);
                }
            } else {
                paidFromFutureInstallments = paidFromFutureInstallments.plus(installment.getInterestPaid(currency))
                        .plus(installment.getPenaltyChargesPaid(currency)).plus(installment.getFeeChargesPaid(currency));
            }

        }
        balances[0] = interest;
        balances[1] = fee;
        balances[2] = penalty;
        balances[3] = paidFromFutureInstallments;
        return balances;
    }

}
