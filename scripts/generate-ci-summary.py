#!/usr/bin/env python3
import os
import sys
import glob
import xml.etree.ElementTree as ET

def main():
    print("Parsing JaCoCo and Surefire test reports...")

    # 1. Parse JaCoCo coverage
    total_lines_cov = 0
    total_lines_miss = 0
    total_branches_cov = 0
    total_branches_miss = 0
    module_summaries = []

    for jacoco_xml in sorted(glob.glob("extensions/**/target/site/jacoco/jacoco.xml", recursive=True)):
        try:
            tree = ET.parse(jacoco_xml)
            root = tree.getroot()
            module_name = root.attrib.get("name")
            if not module_name:
                parts = jacoco_xml.replace("\\", "/").split("/")
                module_name = parts[2] if len(parts) > 2 else os.path.dirname(jacoco_xml)

            m_line_cov = 0
            m_line_miss = 0
            m_branch_cov = 0
            m_branch_miss = 0

            for counter in root.findall("counter"):
                c_type = counter.attrib.get("type")
                covered = int(counter.attrib.get("covered", 0))
                missed = int(counter.attrib.get("missed", 0))

                if c_type == "LINE":
                    m_line_cov += covered
                    m_line_miss += missed
                elif c_type == "BRANCH":
                    m_branch_cov += covered
                    m_branch_miss += missed

            total_lines_cov += m_line_cov
            total_lines_miss += m_line_miss
            total_branches_cov += m_branch_cov
            total_branches_miss += m_branch_miss

            m_line_total = m_line_cov + m_line_miss
            m_line_pct = (m_line_cov / m_line_total * 100) if m_line_total > 0 else 100.0

            m_branch_total = m_branch_cov + m_branch_miss
            m_branch_pct = (m_branch_cov / m_branch_total * 100) if m_branch_total > 0 else 100.0

            module_summaries.append({
                "name": module_name,
                "line_pct": m_line_pct,
                "line_cov": m_line_cov,
                "line_total": m_line_total,
                "branch_pct": m_branch_pct,
                "branch_cov": m_branch_cov,
                "branch_total": m_branch_total
            })
        except Exception as e:
            print(f"Warning: could not parse {jacoco_xml}: {e}")

    total_lines = total_lines_cov + total_lines_miss
    total_line_pct = (total_lines_cov / total_lines * 100) if total_lines > 0 else 0.0

    total_branches = total_branches_cov + total_branches_miss
    total_branch_pct = (total_branches_cov / total_branches * 100) if total_branches > 0 else 0.0

    # 2. Parse Test Reports (Surefire / Failsafe)
    tests_total = 0
    tests_failed = 0
    tests_errors = 0
    tests_skipped = 0
    tests_time = 0.0

    for test_xml in glob.glob("extensions/**/target/*-reports/TEST-*.xml", recursive=True):
        try:
            tree = ET.parse(test_xml)
            root = tree.getroot()
            tests_total += int(root.attrib.get("tests", 0))
            tests_failed += int(root.attrib.get("failures", 0))
            tests_errors += int(root.attrib.get("errors", 0))
            tests_skipped += int(root.attrib.get("skipped", 0))
            tests_time += float(root.attrib.get("time", 0.0))
        except Exception as e:
            print(f"Warning: could not parse {test_xml}: {e}")

    tests_passed = tests_total - tests_failed - tests_errors - tests_skipped

    def status_badge(pct):
        if pct >= 80:
            return "🟢"
        elif pct >= 60:
            return "🟡"
        else:
            return "🔴"

    # 3. Generate Markdown
    lines = []
    lines.append("## 📊 CI Test & Coverage Report")
    lines.append("")
    lines.append("| Metric | Result | Status |")
    lines.append("| :--- | :--- | :---: |")
    test_status = "✅ Passed" if (tests_failed == 0 and tests_errors == 0) else "❌ Failed"
    lines.append(f"| **Tests Executed** | **{tests_total}** ({tests_passed} passed, {tests_failed} failed, {tests_errors} errors, {tests_skipped} skipped in {tests_time:.2f}s) | {test_status} |")
    lines.append(f"| **Line Coverage** | **{total_line_pct:.1f}%** ({total_lines_cov} / {total_lines} lines) | {status_badge(total_line_pct)} |")
    lines.append(f"| **Branch Coverage** | **{total_branch_pct:.1f}%** ({total_branches_cov} / {total_branches} branches) | {status_badge(total_branch_pct)} |")
    lines.append("")

    if module_summaries:
        lines.append("### 📦 Module Breakdown")
        lines.append("")
        lines.append("| Extension Module | Line Coverage | Branch Coverage |")
        lines.append("| :--- | :---: | :---: |")
        for m in module_summaries:
            lines.append(f"| `{m['name']}` | {status_badge(m['line_pct'])} **{m['line_pct']:.1f}%** ({m['line_cov']}/{m['line_total']}) | {status_badge(m['branch_pct'])} **{m['branch_pct']:.1f}%** ({m['branch_cov']}/{m['branch_total']}) |")
        lines.append("")

    lines.append("> ℹ️ *Full HTML reports are available as downloadable build artifacts (`jacoco-coverage-reports` and `surefire-test-reports`).*")

    md_content = "\n".join(lines) + "\n"

    # Save to file
    out_file = sys.argv[1] if len(sys.argv) > 1 else "ci-summary.md"
    with open(out_file, "w", encoding="utf-8") as f:
        f.write(md_content)

    print(f"CI Summary written to {out_file}")

    # Also append to GITHUB_STEP_SUMMARY if present
    step_summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if step_summary_path and os.path.exists(step_summary_path):
        with open(step_summary_path, "a", encoding="utf-8") as f:
            f.write("\n" + md_content)
        print("CI Summary appended to GITHUB_STEP_SUMMARY")

if __name__ == "__main__":
    main()
