/*
 * Copyright (C) 2020 Graylog, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */
import * as React from 'react';
import styled from 'styled-components';
import { useContext } from 'react';

import { MenuItem } from 'components/graylog';
import ActionMenuItem from 'views/components/actions/ActionMenuItem';
import FieldAndValueActionsContext from 'views/components/contexts/FieldAndValueActionsContext';
import { ActionDefinition, ActionHandlerArguments } from 'views/components/actions/ActionHandler';

const DropdownHeader = styled.span`
  padding-left: 10px;
  padding-right: 10px;
  padding-bottom: 5px;
  margin-bottom: 5px;
  font-weight: 600;
`;

const StyledListItem = styled.li`
  margin-bottom: 10px;
  list-style: none;
`;

const filterVisibleActions = (actions: Array<ActionDefinition> | undefined = [], handlerArgs: Props['handlerArgs']) => {
  return actions?.filter((action: ActionDefinition) => {
    const { isHidden = () => false } = action;

    return !isHidden(handlerArgs);
  });
};

const useInternalActions = (type: Props['type'], handlerArgs: Props['handlerArgs']) => {
  const fieldAndValueActions = useContext(FieldAndValueActionsContext);

  if (type === 'value' && fieldAndValueActions) {
    return filterVisibleActions(fieldAndValueActions.valueActions.internal, handlerArgs);
  }

  if (type === 'field' && fieldAndValueActions) {
    return filterVisibleActions(fieldAndValueActions.fieldActions.internal, handlerArgs);
  }

  return [];
};

const useExternalActions = (type: Props['type'], handlerArgs: Props['handlerArgs']) => {
  const fieldAndValueActions = useContext(FieldAndValueActionsContext);

  if (type !== 'value' || !fieldAndValueActions) {
    return [];
  }

  return filterVisibleActions(fieldAndValueActions.valueActions.external, handlerArgs);
};

type Props = {
  children: React.ReactNode,
  type: 'field' | 'value',
  handlerArgs: ActionHandlerArguments,
  setOverflowingComponents: (components: React.ReactNode) => void,
  overflowingComponents: React.ReactNode,
  onMenuToggle: () => void,
};

const ActionDropdown = ({
  children,
  type,
  handlerArgs,
  setOverflowingComponents,
  overflowingComponents,
  onMenuToggle,
}: Props) => {
  const internalActions = useInternalActions(type, handlerArgs);
  const externalActions = useExternalActions(type, handlerArgs);

  return (
    <>
      <StyledListItem>
        <DropdownHeader>
          {children}
        </DropdownHeader>
      </StyledListItem>

      <MenuItem divider />
      <MenuItem header>Actions</MenuItem>
      {internalActions.map((action) => (
        <ActionMenuItem action={action}
                        key={`${type}-action-${action.type}`}
                        handlerArgs={handlerArgs}
                        setOverflowingComponents={setOverflowingComponents}
                        overflowingComponents={overflowingComponents}
                        type={type}
                        onMenuToggle={onMenuToggle} />
      ))}

      {(externalActions && externalActions.length !== 0) && (
        <>
          <MenuItem divider />
          {externalActions.map((action) => (
            <ActionMenuItem action={action}
                            key={`${type}-action-${action.type}`}
                            handlerArgs={handlerArgs}
                            setOverflowingComponents={setOverflowingComponents}
                            overflowingComponents={overflowingComponents}
                            type={type}
                            onMenuToggle={onMenuToggle} />
          ))}
        </>
      )}
    </>
  );
};

export default ActionDropdown;