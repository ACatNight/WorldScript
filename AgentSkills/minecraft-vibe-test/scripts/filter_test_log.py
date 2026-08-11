#!/usr/bin/env python3
"""
CobbleVoyage 测试日志过滤器
从 Minecraft 服务器日志中提取 [TEST] 前缀行，彩色高亮显示。

用法:
  python filter_test_log.py <log_file>                    # 显示所有测试日志
  python filter_test_log.py <log_file> --event FAIL       # 只看失败
  python filter_test_log.py <log_file> --event SUMMARY    # 只看摘要
  python filter_test_log.py <log_file> --module team      # 只看 team 模块
  python filter_test_log.py <log_file> --event FAIL --module loot  # 组合过滤
  python filter_test_log.py <log_file> --follow            # 实时跟踪（类似 tail -f）

事件类型: START, PASS, FAIL, ERROR, SUMMARY
模块名称: ship, engine, team, loot（及未来新增的模块）
"""

import sys
import re
import argparse
import time
import os

# ANSI 颜色
GREEN = "\033[92m"
RED = "\033[91m"
YELLOW = "\033[93m"
CYAN = "\033[96m"
MAGENTA = "\033[95m"
RESET = "\033[0m"
BOLD = "\033[1m"
DIM = "\033[2m"

EVENT_COLORS = {
    "START": CYAN,
    "PASS": GREEN,
    "FAIL": RED,
    "ERROR": MAGENTA,
    "SUMMARY": YELLOW,
}

# 匹配 [TEST] 行，兼容带时间戳的服务器日志格式
# 例: [12:34:56 INFO]: [TEST] PASS ship.repairCost.full: 满耐久维修费=0
# 或: [TEST] PASS ship.repairCost.full: 满耐久维修费=0
TEST_PATTERN = re.compile(r"\[TEST\]\s+(START|PASS|FAIL|ERROR|SUMMARY)\s+(.+)")


def colorize(event: str, line: str) -> str:
    color = EVENT_COLORS.get(event, RESET)
    if event == "FAIL" or event == "ERROR":
        return f"{color}{BOLD}{line}{RESET}"
    if event == "SUMMARY":
        return f"{color}{BOLD}{line}{RESET}"
    if event == "PASS":
        return f"{color}{line}{RESET}"
    return f"{color}{line}{RESET}"


def extract_module(rest: str) -> str:
    """从 [TEST] 后的内容提取模块名，如 'team.typeBonus.singleWater' -> 'team'"""
    return rest.split(".")[0].split(":")[0].strip()


def process_line(line: str, event_filter: str = None, module_filter: str = None) -> str | None:
    match = TEST_PATTERN.search(line)
    if not match:
        return None

    event = match.group(1)
    rest = match.group(2)

    if event_filter and event != event_filter.upper():
        return None

    module = extract_module(rest)
    if module_filter and module != module_filter.lower():
        return None

    test_part = f"[TEST] {event} {rest}"
    return colorize(event, test_part)


def process_file(filepath: str, event_filter: str = None, module_filter: str = None):
    stats = {"START": 0, "PASS": 0, "FAIL": 0, "ERROR": 0, "SUMMARY": 0}
    with open(filepath, "r", encoding="utf-8", errors="replace") as f:
        for line in f:
            result = process_line(line.rstrip(), event_filter, module_filter)
            if result:
                print(result)
                for ev in stats:
                    if f"[TEST] {ev}" in result or f"\033[" in result:
                        pass
                match = TEST_PATTERN.search(line)
                if match:
                    stats[match.group(1)] += 1

    if not event_filter:
        print(f"\n{DIM}--- 统计 ---{RESET}")
        print(f"  {GREEN}PASS:  {stats['PASS']}{RESET}")
        print(f"  {RED}FAIL:  {stats['FAIL']}{RESET}")
        print(f"  {MAGENTA}ERROR: {stats['ERROR']}{RESET}")
        print(f"  总计:  {stats['PASS'] + stats['FAIL'] + stats['ERROR']}")


def follow_file(filepath: str, event_filter: str = None, module_filter: str = None):
    """实时跟踪日志文件（类似 tail -f）"""
    print(f"{CYAN}跟踪日志: {filepath} (Ctrl+C 退出){RESET}\n")
    with open(filepath, "r", encoding="utf-8", errors="replace") as f:
        f.seek(0, 2)  # 跳到文件末尾
        while True:
            line = f.readline()
            if line:
                result = process_line(line.rstrip(), event_filter, module_filter)
                if result:
                    print(result)
            else:
                time.sleep(0.3)


def main():
    parser = argparse.ArgumentParser(
        description="CobbleVoyage 测试日志过滤器",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  %(prog)s server.log                        显示所有测试日志
  %(prog)s server.log --event FAIL           只看失败的测试
  %(prog)s server.log --event SUMMARY        只看模块摘要
  %(prog)s server.log --module team          只看 team 模块
  %(prog)s server.log -e FAIL -m loot        组合过滤
  %(prog)s server.log --follow               实时跟踪
        """,
    )
    parser.add_argument("logfile", help="服务器日志文件路径")
    parser.add_argument(
        "-e", "--event",
        choices=["START", "PASS", "FAIL", "ERROR", "SUMMARY"],
        help="按事件类型过滤",
    )
    parser.add_argument("-m", "--module", help="按模块名过滤 (ship/engine/team/loot/...)")
    parser.add_argument("-f", "--follow", action="store_true", help="实时跟踪日志")

    args = parser.parse_args()

    if not os.path.exists(args.logfile):
        print(f"{RED}文件不存在: {args.logfile}{RESET}", file=sys.stderr)
        sys.exit(1)

    if args.follow:
        try:
            follow_file(args.logfile, args.event, args.module)
        except KeyboardInterrupt:
            print(f"\n{DIM}已停止跟踪{RESET}")
    else:
        process_file(args.logfile, args.event, args.module)


if __name__ == "__main__":
    main()
