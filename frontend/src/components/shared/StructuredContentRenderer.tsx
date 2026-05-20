import React from 'react';
import { Typography, Image, Empty, theme } from 'antd';
import type { StructuredContentNode } from '@/types';
import '../../styles/components.css';

interface StructuredContentRendererProps {
  content: StructuredContentNode[];
}

const renderNode = (
  node: StructuredContentNode,
  index: number,
  token: ReturnType<typeof theme.useToken>['token']
): React.ReactNode => {
  switch (node.type) {
    case 'heading': {
      const level = node.level ?? 2;
      const clampedLevel = Math.max(1, Math.min(5, level)) as 1 | 2 | 3 | 4 | 5;
      return (
        <Typography.Title
          key={index}
          level={clampedLevel}
          style={{ marginTop: index === 0 ? 0 : token.marginLG }}
        >
          {node.text}
        </Typography.Title>
      );
    }

    case 'paragraph':
      return (
        <Typography.Paragraph
          key={index}
          style={{ fontSize: token.fontSize, lineHeight: token.lineHeight }}
        >
          {node.text}
        </Typography.Paragraph>
      );

    case 'image':
      return (
        <Image
          key={index}
          src={node.src}
          alt={node.alt ?? ''}
          fallback="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAMIAAADDCAYAAADQvc6UAAABRWlDQ1BJQ0MgUHJvZmlsZQAAKJFjYGASSSwoyGFhYGDIzSspCnJ3UoiIjFJgf8LAwSDCIMogwMCcmFxc4BgQ4ANUwgCjUcG3awyMIPqyLsis7PPOq3QO0E2LJPgLYnqPAg4CJXgBAZYAsDIB2HZBdkVpBYnIGkDsBWS7AIWTEoCSPUD2HhBwNTIoSqA3kK0HUHtGQewZYPYgADk4Mak4EVgwMqBgZWBg5oA4P5JmRWUG4yQoKGsDMHoxAgifDzAwK8IAMLgDYUkFBQ/DMzRwMNQnFJQJlkAyH1hdJ0cXU3JPIDi0GBiYGRjA6mQWJIA4HQbGzsyGkWEhBQYG/ZOA4f/CLIaMXVsYGJgWAAZ2IkkJsHwZGBiYDQwM34UQcx4Dw20GBgdmBgaG/YMoLgPBi4GB4Q0MDH8hxBgZBgYqBgYKB8bGhWAwAQBSeBJeNRsIngAAAGAUExURf///wAAAPf390TH6Pf3/+/v7+/3/+/39+/39+/v7wAAAPf//+/39wAAAPf390LE5jGMyjmm1AAAAPf//+/3/wAAAPf//0PF50PF5jGMyjGMyjGMyjGMyjGMyjmm1TGMyvf390TH6Pf3/0TH6Pf390PF5jmm1TGMyjGMyjmm1Tmm1Tmm1Tmm1ff//0PF5jmm1ff390PF5vf//zmm1Tmm1Tmm1Tmm1Tmm1ff/9zGMyvf39zmm1TGMyjmm1TGMyvf390PF5jGMykPF5sPDwzmm1ff390PF5zmm1TGMyjmm1TGMyvf//zmm1TGMyjmm1Tmm1Tmm1ff/90PF5kTF5kTH6Dmm1THM7AAAADFENbAAAAAdFJOU////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////wAXi4sAAAACYktHRAD/h4/M4M8AAAAJcEhZcwAADsMAAA7DAcdvqGQAAAAHdElNRQ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"
          style={{ maxWidth: '100%', borderRadius: token.borderRadius }}
        />
      );

    case 'blockquote':
      return (
        <blockquote key={index}>{node.text}</blockquote>
      );

    case 'list': {
      const ListTag = node.ordered ? 'ol' : 'ul';
      return (
        <Typography.Paragraph key={index}>
          <ListTag>
            {node.items?.map((item, i) => (
              <li key={i}>{item}</li>
            ))}
          </ListTag>
        </Typography.Paragraph>
      );
    }

    case 'code':
      return (
        <pre key={index}>
          <code>{node.code}</code>
        </pre>
      );

    default:
      return null;
  }
};

export const StructuredContentRenderer: React.FC<StructuredContentRendererProps> = ({
  content,
}) => {
  const { token } = theme.useToken();

  if (!content || content.length === 0) {
    return <Empty description="暂无正文内容" />;
  }

  return (
    <div
      className="structured-content"
      style={{ lineHeight: token.lineHeight }}
    >
      {content.map((node, index) => renderNode(node, index, token))}
    </div>
  );
};
