package com.derppening.researchprojecttoolkit.util

import kotlin.math.abs
import kotlin.math.sqrt

data class NumericStatistic(
    val p: Int,
    val n: Int,
    val pp: Int,
    val pn: Int,
    val tp: Int,
    val fp: Int,
    val tn: Int,
    val fn: Int
) {

    val population = p + n

    val tpr = tp.toDouble() / p.toDouble()
    val fnr = fn.toDouble() / p.toDouble()
    val fpr = fp.toDouble() / n.toDouble()
    val tnr = tn.toDouble() / n.toDouble()

    val accuracy = (tp + tn).toDouble() / population.toDouble()
    val ppv = tp.toDouble() / pp.toDouble()
    val f1 = (2 * tp).toDouble() / (2 * tp + fp + fn).toDouble()

    val informedness = tpr + tnr - 1
    // Prevalence Threshold
    val pt = (sqrt(tpr * fpr) - fpr) / (tpr + fpr)
    val prevalence = p.toDouble() / population.toDouble()
    val falseOmissionRate = fn.toDouble() / pn.toDouble()
    val lrPlus = tpr / fpr
    val lrMinus = fnr / tnr
    // False discovery rate
    val fdr = fp.toDouble() / pp.toDouble()
    // Negative predictive value
    val npv = tn.toDouble() / pn.toDouble()
    // Markedness
    val mk = ppv + npv - 1
    // Diagnostic odds ratio
    val dor = lrPlus / lrMinus
    val balancedAccuracy = (tpr + tnr) / 2.0
    // Fowlkes-Mallows index
    val fm = sqrt(ppv * tpr)
    // Matthews correlation coefficient
    val mcc = sqrt(tpr * tnr * ppv * npv) - sqrt(fnr * fpr * falseOmissionRate * fdr)
    // Threat score
    val ts = tp.toDouble() / (tp + fn + fp).toDouble()

    init {
        check(p + n == population) { "(p + n) (which is ${p + n}) != population (which is $population)" }
        check(pp + pn == population) { "(pp + pn) (which is ${pp + pn}) != population (which is $population)" }
        check((tp + fp + fn + tn) == population) { "(tp + fp + fn + tn) (which is ${tp + fp + fn + tn}) != population (which is $population)" }

        if (tpr.isFinite() && fnr.isFinite()) {
            check(abs(tpr - (1.0 - fnr)) < EPSILON) {
                "tpr (which is ${tpr.toStringRounded(3)}) != (1.0 - fnr) (which is ${(1.0 - fnr).toStringRounded(3)})"
            }
            check(abs(fnr - (1.0 - tpr)) < EPSILON) {
                "fnr (which is ${fnr.toStringRounded(3)}) != (1.0 - tpr) (which is ${(1.0 - tpr).toStringRounded(3)})"
            }
        }

        if (fpr.isFinite() && tnr.isFinite()) {
            check(abs(fpr - (1.0 - tnr)) < EPSILON) {
                "fpr (which is ${fpr.toStringRounded(3)}) != (1.0 - tnr) (which is ${(1.0 - tnr).toStringRounded(3)})"
            }
            check(abs(tnr - (1.0 - fpr)) < EPSILON) {
                "tnr (which is ${tnr.toStringRounded(3)}) != (1.0 - fpr) (which is ${(1.0 - fpr).toStringRounded(3)})"
            }
        }

        if (ppv.isFinite() && fdr.isFinite()) {
            check(abs(ppv - (1.0 - fdr)) < EPSILON) {
                "ppv (which is ${ppv.toStringRounded(3)}) != (1.0 - fdr) (which is ${(1.0 - fdr).toStringRounded(3)})"
            }
            check(abs(fdr - (1.0 - ppv)) < EPSILON) {
                "fdr (which is ${fdr.toStringRounded(3)}) != (1.0 - ppv) (which is ${(1.0 - ppv).toStringRounded(3)})"
            }
        }

        if (falseOmissionRate.isFinite() && npv.isFinite()) {
            check(abs(falseOmissionRate - (1.0 - npv)) < EPSILON) {
                "for (which is ${falseOmissionRate.toStringRounded(3)}) != (1.0 - npv) (which is ${
                    (1.0 - npv).toStringRounded(3)
                })"
            }
            check(abs(npv - (1.0 - falseOmissionRate)) < EPSILON) {
                "npv (which is ${npv.toStringRounded(3)}) != (1.0 - for) (which is ${
                    (1.0 - falseOmissionRate).toStringRounded(3)
                })"
            }
        }
    }

    companion object {

        private const val EPSILON = 0.0001
    }
}