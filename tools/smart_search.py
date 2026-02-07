#!/usr/bin/env python3
"""
智能代码搜索脚本
基于关键词进行智能过滤，减少全量读取的需求

功能：
1. 多层级搜索：文件名 → 类定义 → 方法定义 → 注释
2. 相关性排序：接口/抽象类优先，核心系统优先
3. 智能预览：提供相关代码片段预览
4. 格式输出：支持JSON和人类可读格式
"""

import os
import re
import sys
import json
import argparse
from pathlib import Path
from typing import List, Dict, Tuple, Optional, Set
from dataclasses import dataclass, field
from enum import Enum


class SearchPriority(Enum):
    """搜索优先级"""
    FILENAME = 1      # 文件名匹配
    CLASS_DEF = 2     # 类/接口定义
    METHOD_DEF = 3    # 方法定义
    COMMENT = 4       # 注释
    CONTENT = 5       # 一般内容


@dataclass
class SearchResult:
    """搜索结果"""
    file_path: str
    line_number: int
    line_content: str
    match_type: SearchPriority
    context_before: List[str] = field(default_factory=list)
    context_after: List[str] = field(default_factory=list)
    score: float = 0.0  # 相关性分数


class SmartSearcher:
    """智能搜索器"""

    def __init__(self, root_dir: str = ".", allowed_dirs: Optional[List[str]] = None):
        self.root_dir = Path(root_dir).resolve()
        self.allowed_dirs = allowed_dirs  # 允许搜索的目录列表
        self.file_extensions = {
            '.java': 'java',
            '.kt': 'kotlin',
            '.py': 'python',
            '.js': 'javascript',
            '.ts': 'typescript',
            '.tsx': 'typescript-react',
            '.jsx': 'javascript-react',
            '.md': 'markdown',
            '.txt': 'text',
            '.json': 'json',
            '.xml': 'xml',
            '.yml': 'yaml',
            '.yaml': 'yaml',
            '.gradle': 'gradle',
            '.properties': 'properties'
        }

        # 忽略的目录模式
        self.ignore_patterns = [
            '**/node_modules/**',
            '**/build/**',
            '**/.gradle/**',
            '**/.git/**',
            '**/target/**',
            '**/dist/**',
            '**/.claude/**',
            '**/.cursor/**',
            '**/.specify/**',
            '**/.windsurf/**',
            '**/.*'  # 隐藏目录
        ]

        # 关键词类别映射（用于智能搜索策略）
        self.keyword_categories = {
            'interface': ['interface', 'protocol', 'api', 'contract'],
            'class': ['class', 'struct', 'record', 'data class'],
            'abstract': ['abstract', 'base', 'template'],
            'game_core': ['game', 'player', 'world', 'state', 'entity', 'system'],
            'ui': ['ui', 'view', 'screen', 'panel', 'widget', 'component'],
            'config': ['config', 'setting', 'property', 'option', 'define'],
            'service': ['service', 'manager', 'handler', 'controller', 'processor'],
        }

    def is_in_allowed_dirs(self, file_path: Path) -> bool:
        """检查文件是否在允许的目录中"""
        if not self.allowed_dirs:
            return True  # 如果没有限制，允许所有目录

        file_path_str = str(file_path.relative_to(self.root_dir))
        for allowed_dir in self.allowed_dirs:
            if file_path_str.startswith(allowed_dir):
                return True
        return False

    def should_ignore(self, file_path: Path) -> bool:
        """检查文件是否应该被忽略"""
        path_str = str(file_path)
        for pattern in self.ignore_patterns:
            if file_path.match(pattern):
                return True
        return False

    def get_file_type(self, file_path: Path) -> Optional[str]:
        """获取文件类型"""
        ext = file_path.suffix.lower()
        return self.file_extensions.get(ext)

    def categorize_query(self, query: str) -> Dict[str, bool]:
        """分析查询关键词的类别"""
        query_lower = query.lower()
        categories = {cat: False for cat in self.keyword_categories}

        for category, keywords in self.keyword_categories.items():
            for keyword in keywords:
                if keyword in query_lower:
                    categories[category] = True
                    break

        # 特殊模式检测
        if re.search(r'class\s+\w+', query, re.IGNORECASE):
            categories['class'] = True
        if re.search(r'interface\s+\w+', query, re.IGNORECASE):
            categories['interface'] = True
        if re.search(r'abstract\s+class\s+\w+', query, re.IGNORECASE):
            categories['abstract'] = True

        return categories

    def calculate_score(self, result: SearchResult, query_categories: Dict[str, bool]) -> float:
        """计算相关性分数"""
        score = 0.0

        # 基础分数基于匹配类型
        match_type_scores = {
            SearchPriority.FILENAME: 10.0,
            SearchPriority.CLASS_DEF: 8.0,
            SearchPriority.METHOD_DEF: 6.0,
            SearchPriority.COMMENT: 4.0,
            SearchPriority.CONTENT: 2.0,
        }
        score += match_type_scores.get(result.match_type, 1.0)

        # 文件名匹配增强
        if result.match_type == SearchPriority.FILENAME:
            score += 5.0

        # 类/接口定义增强
        if result.match_type == SearchPriority.CLASS_DEF:
            line_lower = result.line_content.lower()
            if 'interface' in line_lower and query_categories.get('interface'):
                score += 6.0  # 接口匹配
            elif 'abstract' in line_lower and query_categories.get('abstract'):
                score += 5.0  # 抽象类匹配
            elif 'class' in line_lower and query_categories.get('class'):
                score += 4.0  # 类匹配

        # 核心系统文件增强
        file_path_str = str(result.file_path)
        if any(core in file_path_str.lower() for core in ['game', 'player', 'world', 'entity']):
            score += 3.0

        return score

    def search_file(self, file_path: Path, query: str, query_categories: Dict[str, bool]) -> List[SearchResult]:
        """在单个文件中搜索"""
        results = []
        query_pattern = re.compile(re.escape(query), re.IGNORECASE)

        try:
            with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                lines = f.readlines()

            for i, line in enumerate(lines):
                line_stripped = line.rstrip('\n')

                # 检查各种匹配类型
                match_type = None
                matched = False

                # 1. 类/接口定义匹配
                if re.search(r'(class|interface|abstract\s+class|record|enum)\s+\w+', line_stripped, re.IGNORECASE):
                    if query_pattern.search(line_stripped):
                        match_type = SearchPriority.CLASS_DEF
                        matched = True

                # 2. 方法定义匹配 (Java/Kotlin)
                elif re.search(r'(public|private|protected|\s+)?\w+\s+\w+\s*\([^)]*\)\s*\{?', line_stripped):
                    if query_pattern.search(line_stripped):
                        match_type = SearchPriority.METHOD_DEF
                        matched = True

                # 3. 注释匹配
                elif re.search(r'//|/\*|\*|#|<!--|//|"""|\'\'\'', line_stripped):
                    if query_pattern.search(line_stripped):
                        match_type = SearchPriority.COMMENT
                        matched = True

                # 4. 一般内容匹配
                elif query_pattern.search(line_stripped):
                    match_type = SearchPriority.CONTENT
                    matched = True

                if matched and match_type:
                    # 添加上下文
                    context_before = lines[max(0, i-2):i]
                    context_after = lines[i+1:min(len(lines), i+3)]

                    result = SearchResult(
                        file_path=str(file_path),
                        line_number=i+1,
                        line_content=line_stripped,
                        match_type=match_type,
                        context_before=[l.rstrip('\n') for l in context_before],
                        context_after=[l.rstrip('\n') for l in context_after]
                    )

                    # 计算分数
                    result.score = self.calculate_score(result, query_categories)
                    results.append(result)

        except (IOError, UnicodeDecodeError) as e:
            # 跳过无法读取的文件
            pass

        return results

    def search_filename(self, query: str) -> List[SearchResult]:
        """搜索文件名匹配"""
        results = []
        query_pattern = re.compile(re.escape(query), re.IGNORECASE)

        for file_path in self.root_dir.rglob('*'):
            if self.should_ignore(file_path):
                continue

            if file_path.is_file() and query_pattern.search(file_path.name):
                result = SearchResult(
                    file_path=str(file_path),
                    line_number=0,
                    line_content=f"File: {file_path.name}",
                    match_type=SearchPriority.FILENAME,
                    score=15.0  # 文件名匹配分数较高
                )
                results.append(result)

        return results

    def smart_search(self, query: str, max_results: int = 20, file_types: Optional[List[str]] = None) -> List[SearchResult]:
        """
        智能搜索主函数

        Args:
            query: 搜索查询
            max_results: 最大结果数
            file_types: 限制文件类型列表（如['java', 'python', 'markdown']）

        Returns:
            排序后的搜索结果列表
        """
        print(f"🔍 智能搜索: '{query}'", file=sys.stderr)
        if file_types:
            print(f"   限制类型: {', '.join(file_types)}", file=sys.stderr)
        if self.allowed_dirs:
            print(f"   搜索目录: {', '.join(self.allowed_dirs)}", file=sys.stderr)

        # 分析查询类别
        query_categories = self.categorize_query(query)

        all_results = []

        # 第1阶段：文件名搜索（最高优先级）
        print("  阶段1: 搜索文件名匹配...", file=sys.stderr)
        filename_results = self.search_filename(query)
        if file_types:
            filename_results = [r for r in filename_results if self.get_file_type(Path(r.file_path)) in file_types]
        if self.allowed_dirs:
            filename_results = [r for r in filename_results if self.is_in_allowed_dirs(Path(r.file_path))]
        all_results.extend(filename_results)

        # 第2阶段：内容搜索
        print("  阶段2: 搜索文件内容...", file=sys.stderr)
        files_searched = 0

        for file_path in self.root_dir.rglob('*'):
            if self.should_ignore(file_path):
                continue

            if not file_path.is_file():
                continue

            # 目录过滤（提前过滤以减少IO）
            if not self.is_in_allowed_dirs(file_path):
                continue

            # 文件类型过滤（提前过滤以减少IO）
            if file_types:
                actual_type = self.get_file_type(file_path)
                if actual_type not in file_types:
                    continue

            # 搜索文件内容
            file_results = self.search_file(file_path, query, query_categories)
            all_results.extend(file_results)

            files_searched += 1
            if files_searched % 100 == 0:
                print(f"    已搜索 {files_searched} 个文件...", file=sys.stderr)

        # 按分数排序
        all_results.sort(key=lambda x: x.score, reverse=True)

        # 限制结果数量
        final_results = all_results[:max_results]

        print(f"✅ 搜索完成: 搜索了 {files_searched} 个文件, 找到 {len(all_results)} 个匹配, 显示前 {len(final_results)} 个", file=sys.stderr)

        return final_results

    def format_results(self, results: List[SearchResult], format: str = 'human') -> str:
        """格式化输出结果"""
        if format == 'json':
            result_dicts = []
            for r in results:
                result_dicts.append({
                    'file': r.file_path,
                    'line': r.line_number,
                    'content': r.line_content,
                    'type': r.match_type.name,
                    'score': r.score,
                    'context_before': r.context_before,
                    'context_after': r.context_after
                })
            return json.dumps(result_dicts, indent=2, ensure_ascii=False)

        elif format == 'human':
            output = []
            for i, r in enumerate(results, 1):
                # 文件路径（相对路径）
                rel_path = os.path.relpath(r.file_path, self.root_dir)

                output.append(f"\n{'='*80}")
                output.append(f"#{i} [{r.match_type.name}] 分数: {r.score:.1f}")
                output.append(f"文件: {rel_path}")
                if r.line_number > 0:
                    output.append(f"行号: {r.line_number}")

                # 显示上下文
                if r.context_before:
                    for line in r.context_before:
                        output.append(f"  {line}")

                output.append(f"> {r.line_content}")

                if r.context_after:
                    for line in r.context_after:
                        output.append(f"  {line}")

                output.append(f"{'='*80}")

            return '\n'.join(output)

        elif format == 'simple':
            output = []
            # 按文件路径分组结果
            from collections import defaultdict
            files_dict = defaultdict(list)

            for r in results:
                rel_path = os.path.relpath(r.file_path, self.root_dir)
                files_dict[rel_path].append(r)

            # 按文件输出
            file_count = 1
            for rel_path in sorted(files_dict.keys()):
                matches = files_dict[rel_path]
                output.append(f"\n[{file_count}] {rel_path}")
                output.append(f"{'─' * (len(rel_path) + 8)}")

                for match_idx, r in enumerate(matches, 1):
                    line_info = f":{r.line_number}" if r.line_number > 0 else ""
                    score_info = f" [{r.score:.1f}]" if r.line_number > 0 else ""
                    output.append(f"    {match_idx}. {line_info}{score_info}")
                    output.append(f"       {r.line_content[:95]}{'...' if len(r.line_content) > 95 else ''}")

                file_count += 1

            return '\n'.join(output)

        else:
            return f"未知格式: {format}"

    def get_file_preview(self, file_path: str, lines_around: int = 10) -> str:
        """获取文件预览"""
        try:
            with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                lines = f.readlines()

            preview_lines = []
            for i, line in enumerate(lines[:lines_around], 1):
                preview_lines.append(f"{i:4d}: {line.rstrip()}")

            if len(lines) > lines_around:
                preview_lines.append(f"... 还有 {len(lines) - lines_around} 行")

            return '\n'.join(preview_lines)
        except Exception as e:
            return f"无法读取文件: {e}"


def main():
    parser = argparse.ArgumentParser(description='智能代码搜索工具')
    parser.add_argument('query', help='搜索查询')
    parser.add_argument('--root', default='.', help='根目录路径（默认: 当前目录）')
    parser.add_argument('--types', help='限制文件类型，多个用逗号分隔（如: java,python,markdown）')
    parser.add_argument('--dirs', help='限制搜索的目录，多个用逗号分隔（如: game,web,webnet）')
    parser.add_argument('--max', type=int, default=9999, help='最大结果数（默认: 9999，输出所有结果）')
    parser.add_argument('--format', choices=['human', 'simple', 'json'], default='simple',
                       help='输出格式（默认: simple）')
    parser.add_argument('--output', '-o', help='输出文件路径（默认: 在tools目录下生成 search_result.txt）')
    parser.add_argument('--preview', action='store_true', help='显示第一个结果的文件预览')

    args = parser.parse_args()

    # 解析文件类型
    file_types = None
    if args.types:
        file_types = [t.strip().lower() for t in args.types.split(',')]

    # 解析允许的目录
    allowed_dirs = None
    if args.dirs:
        allowed_dirs = [d.strip() for d in args.dirs.split(',')]

    # 确定输出文件路径
    if args.output:
        output_file = Path(args.output)
    else:
        # 默认输出到 tools 目录下
        output_file = Path(__file__).parent / 'search_result.txt'

    # 创建搜索器
    searcher = SmartSearcher(args.root, allowed_dirs=allowed_dirs)

    # 执行搜索
    results = searcher.smart_search(
        query=args.query,
        max_results=args.max,
        file_types=file_types
    )

    # 格式化输出
    if results:
        output = searcher.format_results(results, args.format)
    else:
        output = "❌ 未找到匹配结果"

    # 同时输出到控制台和文件
    print(output)

    # 写入输出文件
    try:
        with open(output_file, 'w', encoding='utf-8') as f:
            f.write(output)
            if args.preview and results:
                first_result = results[0]
                f.write(f"\n\n{'='*80}\n")
                f.write(f"📄 文件预览: {first_result.file_path}\n")
                f.write(f"{'='*80}\n")
                f.write(searcher.get_file_preview(first_result.file_path))
        print(f"\n✅ 结果已保存到: {output_file}", file=sys.stderr)
    except Exception as e:
        print(f"❌ 保存结果失败: {e}", file=sys.stderr)


if __name__ == '__main__':
    main()