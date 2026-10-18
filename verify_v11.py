"""Reference values for PocketStat v1.1 new procedures (scipy = ground truth)."""
import json
from scipy import stats

out = {}

# 1. One-way ANOVA — classic textbook dataset (3 teaching methods, test scores)
g1 = [6, 8, 4, 5, 3, 4]
g2 = [8, 12, 9, 11, 6, 8]
g3 = [13, 9, 11, 8, 7, 12]
f, p = stats.f_oneway(g1, g2, g3)
out["anova"] = {"F": f, "df1": 2, "df2": 15, "p": p,
                "groups": [g1, g2, g3]}

# 2. Chi-square test of independence — 3x2 contingency table
table = [[10, 10, 20], [20, 10, 10]]
chi2, p, dof, expected = stats.chi2_contingency(table)
out["chisq"] = {"chi2": chi2, "df": dof, "p": p,
                "expected": expected.tolist(), "table": table}

# 3. Pearson correlation
x = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
y = [2.1, 4.0, 6.2, 7.9, 10.1, 12.0, 14.2, 15.8, 18.1, 20.0]
r, p = stats.pearsonr(x, y)
out["corr"] = {"r": r, "p": p, "n": len(x), "x": x, "y": y}

# 4. Simple linear regression
res = stats.linregress(x, y)
out["regress"] = {"slope": res.slope, "intercept": res.intercept,
                  "r2": res.rvalue ** 2, "p_slope": res.pvalue,
                  "stderr_slope": res.stderr, "x": x, "y": y}

# Edge: ANOVA with 2 groups should match t-test^2
a = [78, 82, 85, 79, 88, 81, 76, 84, 80, 83]
b = [72, 75, 70, 78, 74, 71, 76, 73, 69, 77]
f2, p2 = stats.f_oneway(a, b)
t2, pt2 = stats.ttest_ind(a, b, equal_var=True)
out["anova2_vs_ttest"] = {"F": f2, "t2": t2 ** 2, "p_anova": p2, "p_ttest": pt2}

with open("/home/hatch/workspace/pocketstat/verify_v11_ref.json", "w") as fh:
    json.dump(out, fh, indent=1)
for k, v in out.items():
    print(k, {kk: vv for kk, vv in v.items() if kk not in ("groups", "table", "x", "y", "expected")})
